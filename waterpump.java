package vernerP1;

import java.nio.ByteBuffer;
import java.util.Calendar;

import lejos.utility.Delay;

public class WaterPump {
	ArduinoI2CLink theArduino;
	private VernerCtrl theController;
	private Recorder theRecorder;
	
	private int activationTimePerDay[] = new int[356];
	private int activationTimePerHour[] = new int[24];
	private int numberActivationsPerDay[] = new int[356];
	private int lastHour = -1, 
			stopHour, 
			lastDay = -1, 
			day;
	private long startTime = 0;
	private boolean 		isCircOn;
	private byte[] 		bufReadResponseLH = new byte[2];
	private double 		result = 0;
	private ByteBuffer 	wrapped;

	public WaterPump(VernerCtrl aController, ArduinoI2CLink arduino) {
		theArduino = arduino;
		theController = aController;
		theRecorder = new Recorder();
	}

	public Recorder getRecorder() {
		return theRecorder;
	}
	
	public boolean isOn() {
		return isCircOn;
	}

	public synchronized double start() {
		try {
			theArduino.getData('C', bufReadResponseLH, bufReadResponseLH.length);
			isCircOn = true;
		} catch (Exception e) {
			theController.logWithoutDetails("Error tuning circ On on Arduino" + e, 0);
			return 0;
		}

		wrapped = ByteBuffer.wrap(bufReadResponseLH);
		result = wrapped.getShort();
		theRecorder.startRecord();

		Delay.msDelay(10);	

		return ((double) result);
	}

	public synchronized double stop() {
		if (isCircOn) {
			theRecorder.recordInfoAfterStop();
		}

		try {
			theArduino.getData('D', bufReadResponseLH, bufReadResponseLH.length);
			isCircOn = false;
		} catch (Exception e) {
			theController.logWithoutDetails("Error tuning circ Off on Arduino" + e, 0);
			return 0;
		}

		wrapped = ByteBuffer.wrap(bufReadResponseLH);
		result = wrapped.getShort();
		Delay.msDelay(10);	

		return ((double) result);
	}

}
