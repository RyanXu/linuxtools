/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat - Initial Contribution
 *******************************************************************************/
package org.eclipse.linuxtools.jdt.docker.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.eclipse.jdt.launching.ExecutionArguments;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.VMRunnerConfiguration;
import org.eclipse.linuxtools.docker.core.DockerConnectionManager;
import org.eclipse.linuxtools.docker.core.IDockerImage;
import org.eclipse.linuxtools.internal.docker.core.DockerConnection;
import org.eclipse.osgi.util.NLS;

public class JavaAppInContainerLaunchDelegate extends AbstractJavaLaunchConfigurationDelegate {

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ILaunchConfigurationDelegate#launch(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String, org.eclipse.debug.core.ILaunch, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {

		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}

		monitor.beginTask(NLS.bind("{0}...", new String[]{configuration.getName()}), 3); //$NON-NLS-1$
		// check for cancellation
		if (monitor.isCanceled()) {
			return;
		}
		String connectionURI = configuration.getAttribute("org.eclipse.linuxtools.jdt.docker.launcher.connection.uri", (String) null); //$NON-NLS-1$
		String imageID = configuration.getAttribute("org.eclipse.linuxtools.jdt.docker.launcher.image.id", (String) null); //$NON-NLS-1$

		if (connectionURI == null || imageID == null) {
			return;
		}

		try {
			DockerConnection conn = (DockerConnection) DockerConnectionManager.getInstance().getConnectionByUri(connectionURI);
			IDockerImage img = conn.getImage(imageID);

			monitor.subTask("Verifying launch attributes...");

			String mainTypeName = verifyMainTypeName(configuration);
			IVMInstall vm = new ContainerVMInstall(configuration, img);
			ContainerVMRunner runner = new ContainerVMRunner(vm);

			File workingDir = verifyWorkingDirectory(configuration);
			String workingDirName = null;
			if (workingDir != null) {
				workingDirName = workingDir.getAbsolutePath();
			}

			// Environment variables
			String[] envp= getEnvironment(configuration);

			// Program & VM arguments
			String pgmArgs = getProgramArguments(configuration);
			String vmArgs = getVMArguments(configuration);
			ExecutionArguments execArgs = new ExecutionArguments(vmArgs, pgmArgs);

			// VM-specific attributes
			Map<String, Object> vmAttributesMap = getVMSpecificAttributesMap(configuration);

			// Classpath
			String[] classpath = getClasspath(configuration);

			// Create VM config
			VMRunnerConfiguration runConfig = new VMRunnerConfiguration(mainTypeName, classpath);
			runConfig.setProgramArguments(execArgs.getProgramArgumentsArray());
			runConfig.setEnvironment(envp);

			List<String> finalVMArgs = new ArrayList<> (Arrays.asList(execArgs.getVMArgumentsArray()));
			if (ILaunchManager.DEBUG_MODE.equals(mode)) {
				double version = getJavaVersion(conn, img);
				if (version < 1.5) {
					finalVMArgs.add("-Xdebug"); //$NON-NLS-1$
					finalVMArgs.add("-Xnoagent"); //$NON-NLS-1$
				}

				//check if java 1.4 or greater
				if (version < 1.4) {
					finalVMArgs.add("-Djava.compiler=NONE"); //$NON-NLS-1$
				}
				if (version < 1.5) {
					finalVMArgs.add("-Xrunjdwp:transport=dt_socket,server=y,address=" + 8000); //$NON-NLS-1$
				} else {
					finalVMArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,address=" + 8000); //$NON-NLS-1$
				}
			}

			runConfig.setVMArguments(finalVMArgs.toArray(new String [0]));
			runConfig.setWorkingDirectory(workingDirName);
			runConfig.setVMSpecificAttributesMap(vmAttributesMap);

			// Bootpath
			runConfig.setBootClassPath(getBootpath(configuration));

			// check for cancellation
			if (monitor.isCanceled()) {
				return;
			}

			// stop in main
			prepareStopInMain(configuration);

			// done the verification phase
			monitor.worked(1);

			monitor.subTask("Creating source locator...");
			// set the default source locator if required
			setDefaultSourceLocator(launch, configuration);
			monitor.worked(1);

			// Launch the configuration - 1 unit of work
			runner.run(runConfig, launch, monitor);

			// check for cancellation
			if (monitor.isCanceled()) {
				return;
			}

			if (ILaunchManager.DEBUG_MODE.equals(mode)) {
				ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
				ILaunchConfigurationType type = manager.getLaunchConfigurationType(IJavaLaunchConfigurationConstants.ID_REMOTE_JAVA_APPLICATION);
				ILaunchConfiguration cfgForAttach = type.newInstance(null, "attach_to_container"); //$NON-NLS-1$
				ILaunchConfigurationWorkingCopy wc = cfgForAttach.getWorkingCopy();

				while (runner.getIPAddress() == null || !runner.isListening()) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
					}
				}

				String ip = runner.getIPAddress();
				Map<String, String> map = new HashMap<> ();
				map.put("hostname", ip); //$NON-NLS-1$
				map.put("port", String.valueOf(8000)); //$NON-NLS-1$
				wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CONNECT_MAP, map);
				String projectName = configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, (String) null);
				wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, projectName);
				wc.doSave();
				DebugUITools.launch(cfgForAttach, ILaunchManager.DEBUG_MODE);
			}
		}
		finally {
			monitor.done();
		}
	}

	private double getJavaVersion(DockerConnection conn, IDockerImage img) {
		ImageQuery q = new ImageQuery(conn, img.id());
		double res = q.getJavaVersion();
		q.destroy();
		return res;
	}

}
