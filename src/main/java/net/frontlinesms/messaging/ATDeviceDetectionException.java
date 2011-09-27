package net.frontlinesms.messaging;

/** Exception thrown when detecting an AT device. */
@SuppressWarnings("serial")
public class ATDeviceDetectionException extends Exception {
	public ATDeviceDetectionException(String message) {
		super(message);
	}
}