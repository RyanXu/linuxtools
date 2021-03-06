/*******************************************************************************
 * Copyright (c) 2016, 2018 Red Hat.
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat - Initial Contribution
 *******************************************************************************/

package org.eclipse.linuxtools.internal.docker.core;

import java.util.function.Consumer;

/**
 * A Log consumer that has a {@code ready} flag to delay processing.
 */
public class ProcessLogConsumer {

	private final Consumer<String> consumer;

	private boolean ready = false;

	public ProcessLogConsumer(final Consumer<String> consumer) {
		this.consumer = consumer;
	}

	public boolean isReady() {
		return this.ready;
	}

	public void setReady(boolean ready) {
		this.ready = ready;
	}

	public void process(final String log) {
		consumer.accept(log);
	}
}
