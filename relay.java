package vernerP1;

import java.util.Calendar;

import lejos.hardware.motor.RCXMotor;
import lejos.hardware.port.Port;

public class Relay extends RCXMotor {


	private boolean isInverted = false;
	private Recorder theRecorder = new Recorder();

	public Relay(Port port, boolean inverted, int power) {
		super(port);

		setPower(power);
		isInverted = inverted;
		flt();

	}

	public void setPWM_off() {
		port.setPWMMode(0);
	}

	public void setOn() {
		if (isInverted)
			backward(); 
		else 
			forward();
		
		theRecorder.startRecord();
	}

	public void resetStartTime() {
		theRecorder.resetRecord();
		
	}
	
	public void setOff() {
		if (isMoving()) {
			theRecorder.recordInfoAfterStop();
		}
		flt();
	}
	
	public Recorder getRecorder() {
		return theRecorder;
	}
}