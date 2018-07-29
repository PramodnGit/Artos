package com.arpitos.interfaces;

import com.arpitos.framework.TestObjectWrapper;

public interface TestExecutionListner {

	default void testSuiteExecutionStarted(String description) {
	}

	default void testSuiteExecutionFinished(String description) {
	}

	default void testExecutionStarted(TestObjectWrapper t) {
	}

	default void testExecutionFinished(TestObjectWrapper t) {
	}

	default void testExecutionSkipped(TestObjectWrapper t) {
	}

	default void testExecutionLoopCount(int count) {
	}

}
