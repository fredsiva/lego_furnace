package vernerP1;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;

import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;

import lejos.hardware.Battery;
import lejos.hardware.Button;
import lejos.hardware.Sound;
import lejos.hardware.lcd.*;
import lejos.hardware.port.MotorPort;
import lejos.hardware.port.SensorPort;
import lejos.utility.Delay;

/* TODOs
 *  TODO: detect that date is not in 2017, which can happen if EV3 was fully reset and not well connected to Network.  
 *  	Can cause issues when comparing today() to lastTime if NTP syncs in between...
 *  
 *  TODO: Lights.  Have it green when all ok, Yellow when warnings and Red when Errors
 *  
 *  TODO Simplify main screen, big digits, and have current only with key up/down.
 *  
 *  TODO: Silo se rempli trop dans le coin a gauche de la porte.  Orienter donc la buse pour viser plus vers le long mur du fond.
 *  
 *  TODO: WHen high screw temp and feeding, turn FAN on (slow speed?) to finish burning these pellets. 
 *  FIXME: understand why the Lejos main Java prog sometimes crashes while VernerCtrl continues to run.  Is there a log somewhere?
 *  
 */

/*
 * 1st stability issue was the NXT-EV3 Mindsensor, 2nd was the method getWatertemp on Arduino which was not synchronized.
 * 
 * Adruino: when writing to serial console in the receiveData() method, the I2C error rate goes from 0.15% to 30%!!
 */

/*  Investigations on parameters and curious behaviours
 * 
 * 	3,100 feed,wait stabilizes the gaz temp around 100 deg with water temp around 75deg.
 * 
 * 	Fuse of conveyor switched off, soon after alerts about grating contact not closed.  
 * 		Reason was date one wire of the contactor got disconnected.  
 * 		Because of a bug in the code, the grating was not stopping in case of timeout (only further feed was stopping the grating).
 * 		The grating ultimately blocked the pellets in the conveyor screw, and fuse switched off.   
 * 		Further attempts to run the conveyor manually did not always work because of the blocked pellets. 
 */

/*
 * Note: password of live ev3 is root, VW.4apl
 * 
 * 
 * 
 * Version history:
 * 	0.1 - 5 Nov 2017
 * 		Mostly functional with Lego sensors and motors, no double-relay logic
 * 		Program termination under testing
 * 		Still some constructor crashes in Gyro, probably due to many forced stops
 * 		Ambient temp read with DI sensor
 * 		Light sensor on S1 working through I2C adapter (EV3-NXT)
 * 		count-down displayed on screen beside status changes
 * 		basic regulation of temp around target
 * 		getStatus can also wait for a key to be pressed
 * 		Menu and Test module added (Sensors and Motors)
 * 
 *  0.3 - 20 Nov: 
 *  	Checked Exception propagation to ensure program stops.  
 *  	Send email when starting
 *  
 *  0.5	- 3 Dec
 *  	Cleaned Conveyor class by making methods synchronized and deleting complex IsRunning and IsAutonomous.
 *  	Made conveyor safe for the 2nd relay, by never switching the 2nd relay when the first (Triac is on)
 *  	implemented the refeed after grating
 *  	added pleasePrepareForRestart() in Conveyor to allow multiple stop/start
 *  
 *  0.7 - 19 Dec
 *  	WaterTemp on Arduino linked by I2C
 *  	Ignite() has a flavour which does not use Ignitor (To revive an old fire)
 *  	Tested on Verner with Fan and Feed (No Ignite)
 *  
 *  0.8 - 22 Dec
 *  	Interupts afterburner if water temp drops
 *  
 *  0.9 - 23 Dec
 *  	Stop during night
 *  	Better multiplier around target temp (and fix by using proper float)
 *  	Allow ESC during wait for water drop
 *  	Stop firing when gaz temp are high
 *  	Stop during night
 *  	Timeout in startup menu
 *  	Slow feed (3,27), which ensures that Verner is continuously on when 10 deg outside.
 *
 *	0.10 - 27 Dec
 *		Controls the Water Pump through Arduino Relay.
 *		LCD has now icons for Ignitor and WaterPump
 *
 *  1.0 - 1 Jan
 *  	Works on final acrylic board and new MOS mini-board
 *  	wait 5 mins for temp drops
 *  	removed a few traces
 *  	.txt trace file now contains date
 *  
 *  1.0b
 *  	Second smtp adress since hit 1000 limit...  Have configured SMTP to limit to 2 emails per hour
 *  
 *  1.1 - 5 Jan
 *  	Javadoc enabled
 *  	Gather feeding, Grating, ignition, Fan and WaterPump stats per hour and per day 
 *  	fixed stats if period spans over multiple hours (like for Fan which is ON for long times)
 *  
 *  1.2 - 10 Jan
 *  	WebServer showing basic parameters, and allowing to change target temp
 *  	Dropbox sync of Log files implemented as separated app
 *  
 *  1.3 - 14 Jan
 *  	Add separate stat file.
 *  	Gather min/max/avg temps
 *  	Refactor with TempRecorder
 *  	Grating Relay not working anymore... will need to be replaced.
 *  	Fixed NanoHttp to output exception to log file iso stderr
 *  
 *  1.3b code cleanup
 *  	Making private methods if not used outside
 *  	Helper inner class
 *  
 *  1.3c more cleanup
 *  	created calculateMultiplier()
 *  	keeps track of last 10 log messages, cleaned trace level used
 *  	include last events in email
 *  
 *  1.3d 
 *  	Start WP in case of any kind of temp alarms
 *  
 *  1.4
 *  	Fixing the condition to allow Zero Deg Target Temp (Idling)
 *  	Detecting end of Fire in Run() mode
 *  	WPON et WPOFF commands in WebServer
 *  	line Menu to activate outputs from main screen 
 *  	Two levels of Screw Temp: Alarm and Warning (no zero WT)
 *  	Created Recorder class so that WaterPump can benefit from the tricky split of long up time on several continuous hours
 *  	storing last perfect grating time
 *  
 *  Note: pwd of live EV3 is <VW>.4apl
 */

/**
 * Main class of the Verner Controller.
 * 
 * The init() method will initialize all variables, the run() method will then start the whole controller.
 * A first menu will appear, through method menu() , offering some testing features.
 * 
 * @author fsiva
 *
 */
public class VernerCtrl {
	
	class StatusHelper {
		/**
		 * 
		 * @return a string summarizing the status. Used to log.
		 */
		public String getStatusString() {
		    if (theConveyor == null) {
		    	return (DateFormat.getTimeInstance().format(System.currentTimeMillis()));
		    } else { 
		    	return ("[" + 
						DateFormat.getDateInstance().format(System.currentTimeMillis()) + " " + 
						DateFormat.getTimeInstance().format(System.currentTimeMillis()) +
					"] S" 	+ getState() +
					", " + rt.totalMemory()/1024 + "/" + rt.freeMemory()/1024 + 
					"kb ("	+ getTargetWTemp() + 
					"," 	+ getStoredWTemp() + 
					","		+ getExhTemp() +
					","		+ getScrewTemp() +
					") degs  " 	+ theConveyor.getTimeS() +"s");
		    }
			
		}
		
		public String getVerboseStatusHtmlString() {
			StringBuilder result = new StringBuilder("");
			LoggedEvent ev;
			
			int	hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
			int	dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
			
			if (theConveyor == null) {
		    	result.append(DateFormat.getTimeInstance().format(System.currentTimeMillis()));
		    } else { 
		    	result.append("<table style=\"width:100%\" border=\"1\">")
		    			.append("<tr><th>Attribute</th><th>Value</th></tr>")
		    			.append("<tr><td>Date</td>")
		    				.append("<td>" + DateFormat.getDateInstance().format(System.currentTimeMillis()) + "</td></tr>")
		    			.append("<tr><td>Time</td>")
		    			.append("<td>" + DateFormat.getTimeInstance().format(System.currentTimeMillis())+ "</td></tr>")
		    			.append("<tr><td>State</td>")
		    			.append("<td BGCOLOR=\"" + stateColorHtml[getState()] + "\">" + getStateString()+ "</td></tr>")
						.append("<tr><td>Memory total</td>")
						.append("<td>" + rt.totalMemory()/1024+ "</td></tr>")
						.append("<tr><td>Memory Free</td>")
						.append("<td>" + rt.freeMemory()/1024+ "</td></tr>")
						.append("<tr><td>Target water temp</td>")
						.append("<td>" + getTargetWTemp()+ "</td></tr>")
						.append("<tr><td>Current Water temp</td>")
						.append("<td>" + getStoredWTemp()+ "</td></tr>")
						.append("<tr><td>Exhaust Gaz temps</td>")
						.append("<td>" + getExhTemp()+ "</td></tr>")
						.append("<tr><td>Conveyor Screw Temp</td>")
						.append("<td>" + getScrewTemp()+ "</td></tr>")
						.append("<tr><td>Feed,Waiting times in sec</td>")
						.append("<td>" + theConveyor.getTimeS()+ "</td></tr>")
						.append("<tr><td>Ignitor</td>")
						.append("<td>" + isIgnitorOn()+ "</td></tr>")
						.append("<tr><td>WaterPump</td>")
						.append("<td>" + getWPump().isOn()+ "</td></tr>")
						.append("<tr><td>Fan</td>")
						.append("<td>" + getFan().isOn()+ "</td></tr>")
						.append("</table>");

		    	result.append("<br/>History of today (in secs)<br/>");
		    	
		    	result.append("<table style=\"width:100%\" border=\"1\">")
		    			.append("<tr>")
			    			.append("<th>Hour</th>")
			    			.append("<th>Feed time</th>")
			    			.append("<th>Grating time</th>")
			    			.append("<th>Fan time</th>")
			    			.append("<th>Ign time</th>")
			    			.append("<th>Pump time</th>")
			    			.append("<th>Wat min</th>")
			    			.append("<th>Wat max</th>")
			    			.append("<th>exh min</th>")
			    			.append("<th>exh max</th>")
			    			.append("<th>Screw min</th>")
			    			.append("<th>Screw max</th>")
		    			.append("</tr>");

		    	// Show info for each hour of Today.  Number of daily Activations is only shown for last hour.
		    	for (int i=0; i<=hourOfDay; i++) {
		    		result.append("<tr>")
		    			.append("<td>"+ i +"</td>")
		    			.append("<td>" + theConveyor.getFeedHourlyActivationTime(i) + "</td>")
		    			.append("<td>" + theConveyor.getGratingHourlyActivationTime(i) + "</td>")
		    			.append("<td>" + theFan.getActivationTime(i) + "</td>")
		    			.append("<td>" + theIgnitor.getActivationTime(i) + "</td>")
		    			.append("<td>" + theWaterPump.getRecorder().getActivationTime(i) + "</td>")
		    			.append("<td>" + sondeTempEau.getRecorder().getMinTemp(i) + "</td>")
		    			.append("<td>" + sondeTempEau.getRecorder().getMaxTemp(i) + "</td>")
		    			.append("<td>" + sondeTempFumees.getRecorder().getMinTemp(i) + "</td>")
		    			.append("<td>" + sondeTempFumees.getRecorder().getMaxTemp(i) + "</td>")
		    			.append("<td>" + sondeTempVis.getRecorder().getMinTemp(i) + "</td>")
		    			.append("<td>" + sondeTempVis.getRecorder().getMaxTemp(i) + "</td>")

					.append("</tr>");
		    	}
		    	result.append("</table>");
		    }

	    	result.append("<br/>History of last 10 days<br/>");

	    	result.append("<table style=\"width:100%\" border=\"1\">")
			.append("<tr>")
				.append("<th>Day of Year</th>")
				.append("<th>Feed time</th>")
				.append("<th>Feed act</th>")
				.append("<th>Grating time</th>")
				.append("<th>Grating act</th>")
				.append("<th>Fan time</th>")
				.append("<th>Fan act</th>")
				.append("<th>Ign time</th>")
				.append("<th>Ign act</th>")
				.append("<th>Pump time</th>")
				.append("<th>Pump act</th>")
			.append("</tr>");

			for (int d=dayOfYear; d>dayOfYear-10 && d>0; d--) {
	    		result.append("<tr>")
					.append("<td>"+ d +"</td>")
					.append("<td>" + theConveyor.getFeedDailyActivationTime(d) + "</td>")
		    		.append("<td>" + theConveyor.getFeedNumberActivations(d) + "</td>")
					.append("<td>" + theConveyor.getGratingDailyActivationTime(d) + "</td>")
					.append("<td>" + theConveyor.getGratingNumberActivations(d) +"</td>")
					.append("<td>" + theFan.getDailyActivationTime(d) + "</td>")
					.append("<td>" + theFan.getNumberActivations(d) + "</td>")
					.append("<td>" + theIgnitor.getDailyActivationTime(d) + "</td>")
					.append("<td>" + theIgnitor.getNumberActivations(d) + "</td>")
					.append("<td>" + theWaterPump.getRecorder().getDailyActivationTime(d) + "</td>")
					.append("<td>" + theWaterPump.getRecorder().getNumberActivations(d) + "</td>")
				.append("</tr>");
				
			}
			result.append("</table>");

	    	result.append("<br/>Most recent events<br/>");
	    	result.append("<table style=\"width:100%\" border=\"1\">");

			for (int d=0; d<eventList.size(); d++) {
				ev = (LoggedEvent) eventList.get(d);
				
				if (ev.level <=3) {
					result.append("<tr>");
					result.append("<td BGCOLOR=\"" + levelColorHtml[ev.level] + "\">").append(ev.level).append("</td>");
		    		result.append("<td>").append(ev.status).append("</td>");
		    		result.append("<td>").append(ev.message).append("</td>");
		    		if (ev.exception != null)
		    			result.append("<td>").append(ev.exception).append("</td>");
		    		else
		    			result.append("<td></td>");
		    		result.append("</tr>");
				}
			}

			result.append("</table>");

			return (result.toString());
		}
		
		public String getLatestEventsString() {
			LoggedEvent ev;
			StringBuilder result = new StringBuilder("");

			result.append("Most recent events\n");

			for (int d=0; d<eventList.size(); d++) {
				ev = (LoggedEvent) eventList.get(d);
				
				if (ev.level <=3) {
					result.append(ev.level).append("-");
		    		result.append(ev.status).append(":");
		    		result.append(ev.message);
		    		if (ev.exception != null)
		    			result.append("-").append(ev.exception);
		    		result.append("\n");
				}
			}
			return (result.toString());
		}
		
		/**
		 * Write the stats of last hour in the file.
		 * Should only be called once per hour, otherwise there will be some duplicate lines in the file (may happen in case of stop & restart)
		 */
		public void dumpHourlyStats() {
			int	hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
			int	dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
			
			if (hourOfDay == 0) {
				dayOfYear-=1;
				hourOfDay = 23;
			} else hourOfDay-=1;
			
			try {
				statsBWriter.write(	DateFormat.getDateInstance().format(System.currentTimeMillis()) + "," +
									dayOfYear + "," +
									hourOfDay + "," + 
									theConveyor.getFeedHourlyActivationTime(hourOfDay) + "," +
									theConveyor.getFeedNumberActivations(dayOfYear) + "," + 
									theConveyor.getGratingHourlyActivationTime(hourOfDay) + "," + 
									theConveyor.getGratingNumberActivations(dayOfYear) + "," + 
									theFan.getActivationTime(hourOfDay) + "," +
									theFan.getNumberActivations(dayOfYear) + "," + 
									theIgnitor.getActivationTime(hourOfDay) + "," +
									theIgnitor.getNumberActivations(dayOfYear) + "," + 
									theWaterPump.getRecorder().getActivationTime(hourOfDay) + "," +
									theWaterPump.getRecorder().getNumberActivations(dayOfYear) + "," +
									sondeTempEau.getRecorder().getMinTemp(hourOfDay) + "," + 
									sondeTempEau.getRecorder().getMaxTemp(hourOfDay) + "," + 
									sondeTempFumees.getRecorder().getMinTemp(hourOfDay) + "," + 
									sondeTempFumees.getRecorder().getMaxTemp(hourOfDay) + "," + 
									sondeTempVis.getRecorder().getMinTemp(hourOfDay) + "," + 
									sondeTempVis.getRecorder().getMaxTemp(hourOfDay) 
									);
				statsBWriter.newLine();
				statsBWriter.flush();
			} catch (IOException e) {
				pErrStream.print("Error writing to stat file:"+ e);
				e.printStackTrace(pErrStream);
			}
		}

	}
	
	class TestUtilities {
		private void test() {
			boolean isFeedOn	= false,
					isFanOn 	= false,
					isIgnitorOn = false,
					isGratingOn = false;
			long 	lastTime = 0;	

			int button = Button.ID_ENTER;	// Init with anything else than ESC

			LCD.clear();
			LCD.drawString("L=Fd R=Grat", 0, 5);
			LCD.drawString("D=FAN U=IGN", 0, 6);
			LCD.drawString("ESC = Quit", 0, 7);
			
			Delay.msDelay(100);	// Prevents button bouncing
			log("Test", 2);
			
		
			while (button != Button.ID_ESCAPE) {
				refreshTempValues();

				// Log one line every minute
				if (System.currentTimeMillis() - lastTime > 1000 * 60) {
					log(	"W=" + getStoredWTemp() + 
							" G=" + getExhTemp() + " / " + String.format("%.4f", sondeTempFumees.getRawValue())+ 
							" V=" + getScrewTemp() + " / " +  String.format("%.4f", sondeTempVis.getRawValue()) +
							" Feed=" + (int) (isFeedOn ? 1 : 0) + 
							" Grating=" + (int) (isGratingOn ? 1 : 0) + 
							" Fan=" + (int) (isFanOn ? 1 : 0) + 
							" Ignitor=" + (int) (isIgnitorOn ? 1 : 0) +
							" Contact=" + contacteurFinCourseGrating.getValue(), 2);

					lastTime = System.currentTimeMillis();
				}
				
				
				LCD.drawString( DateFormat.getTimeInstance(DateFormat.SHORT).format(System.currentTimeMillis()).substring(0,5), 0, 0);
				LCD.drawString(String.format("%.3g", Battery.getVoltage()) + "V", 6, 0);
				LCD.drawString(String.format("%3g", Battery.getBatteryCurrent()*1000).substring(0,3) + "mA", 13, 0);
				
				LCD.drawString(	"W " + getStoredWTemp() + "  ", 0,2);
				LCD.drawString(	"G " + getExhTemp() + " / " + String.format("%.4f", sondeTempFumees.getRawValue())+ "  ", 0, 3);
				LCD.drawString(	"V " + getScrewTemp() + " / " +  String.format("%.4f", sondeTempVis.getRawValue()) + "  ", 0, 4);
				LCD.drawString( "C " + contacteurFinCourseGrating.getValue(), 15,3);
				
				LCD.drawString( "" + (int) (isFeedOn ? 1 : 0), 15,5);
				LCD.drawString( "" + (int) (isGratingOn ? 1 : 0), 17,5);
				LCD.drawString( "" + (int) (isFanOn ? 1 : 0), 15,6);
				LCD.drawString( "" + (int) (isIgnitorOn ? 1 : 0), 17,6);
				
				button = Button.getButtons();
				if (button != 0) {

					if (button==Button.ID_LEFT) {
						if (isFeedOn) {
							theConveyor.testStopFeed();
							isFeedOn = false;
						} else {
							theConveyor.testFeed();
							isFeedOn = true;
						}
					} else if (button == Button.ID_RIGHT) {
						if (isGratingOn) {
							theConveyor.testStopGrating();
							isGratingOn = false;
						} else {
							theConveyor.testGrating();
							isGratingOn = true;
						}
					} else if (button == Button.ID_DOWN) {
						if (isFanOn) {
							theFan.stop();
							isFanOn = false;
						} else {
							theFan.start();
							isFanOn = true;
						}
					} else if (button == Button.ID_UP) {
						if (isIgnitorOn) {
							theIgnitor.stop();
							isIgnitorOn = false;
						} else {
							theIgnitor.start();
							isIgnitorOn = true;
						}
					} else if (button == Button.ID_ENTER) {
						theIgnitor.stop();
						theFan.stop();
						theConveyor.testStopFeed();
						theConveyor.testStopGrating();
						isFeedOn=false;
						isFanOn = false;
						isIgnitorOn = false;
						isGratingOn = false;
					}

					// wait Button is released
					while (Button.readButtons() != 0) {
						Delay.msDelay(5);
					}
				}
				
				Delay.msDelay(500);
			}

			theIgnitor.stop();
			theFan.stop();
			theConveyor.testStopFeed();
			theConveyor.testStopGrating();
			isFeedOn=false;
			isFanOn = false;
			isIgnitorOn = false;
			isGratingOn = false;
		
			LCD.clear();
		}

		private void testGrating() {
			long 	lastTime = 0, count;	

			int button = Button.ID_ENTER;	// Init with anything else than ESC

			startBackgroundThreads();	// Make sure the Conveyor is ready to run
			
			LCD.clear();
			
			Delay.msDelay(100);	// Prevents button boucing
			log("Test", 2);
			
			while (button != Button.ID_ESCAPE) {
				refreshTempValues();

				// Log one line every minute
				if (System.currentTimeMillis() - lastTime > 1000 * 60) {
					log(	"W=" + getStoredWTemp() +
							" G=" + getExhTemp() + " / " + String.format("%.4g", sondeTempFumees.getRawValue())+ 
							" V=" + getScrewTemp() + " / " +  String.format("%.4g", sondeTempVis.getRawValue()) +
							" Contact=" + contacteurFinCourseGrating.getValue(), 2);

					lastTime = System.currentTimeMillis();
				}

				LCD.drawString( "C " + contacteurFinCourseGrating.getValue(), 15,3);
				LCD.drawString("R=1 Grat+refeed", 0, 5);
				LCD.drawString("L=Grat no refeed", 0, 6);

				button = Button.getButtons();
				if (button != 0) {

					if (button==Button.ID_LEFT) {
						LCD.clear(5);
						LCD.clear(6);
						theConveyor.oneGratingCycle(false);
					} else if (button == Button.ID_RIGHT) {
						LCD.clear(5);
						LCD.clear(6);
						theConveyor.oneGratingCycle(true);
					} else if (button == Button.ID_DOWN) {
						count = 10;
						
						while (count-- > 0) {
							theConveyor.oneGratingCycle(true);
							Delay.msDelay(2000);
						}
					} else if (button == Button.ID_UP) {
					} else if (button == Button.ID_ENTER) {
						theIgnitor.stop();
						theFan.stop();
						theConveyor.testStopFeed();
						theConveyor.testStopGrating();
					}

					// wait Button is released
					while (Button.readButtons() != 0) {
						Delay.msDelay(5);
					}
				}
				
				Delay.msDelay(100);
			}

			theIgnitor.stop();
			theFan.stop();
			theConveyor.testStopFeed();
		
			stopAllThreads();
			LCD.clear();
		}

		private void testCirc() {
			long 	lastTime = 0;	

			int button = Button.ID_ENTER;	// Init with anything else than ESC
			boolean circIsOn = false;
			startBackgroundThreads();	// Make sure the Conveyor is ready to run
			
			LCD.clear();
			
			Delay.msDelay(100);	// Prevents button boucing
			log("Test Circ", 2);

			theAlarmMonitor.sendEmailAvoidingFlood("Test email with html from EV3", "Test Line1\nLine2\n" + 
					statusHelper.getLatestEventsString());
			
			
			while (button != Button.ID_ESCAPE) {
				refreshTempValues();

				// Log one line every minute
				if (System.currentTimeMillis() - lastTime > 1000 * 60) {
					log(	"W=" + getStoredWTemp() +
							" G=" + getExhTemp() + " / " + String.format("%.4g", sondeTempFumees.getRawValue())+ 
							" V=" + getScrewTemp() + " / " +  String.format("%.4g", sondeTempVis.getRawValue()) +
							" Contact=" + contacteurFinCourseGrating.getValue(), 2);

					lastTime = System.currentTimeMillis();
				}

				LCD.drawString( "C " + (int) (circIsOn ? 1 : 0), 15,3);
				LCD.drawString("Right=Circ ON", 0, 4);
				LCD.drawString("Left=Circ OFF", 0, 5);
				LCD.drawString("Up=Email", 0, 6);

				button = Button.getButtons();
				if (button != 0) {

					if (button==Button.ID_LEFT) {
						LCD.clear(5);
						LCD.clear(6);
						getWPump().stop();
					} else if (button == Button.ID_RIGHT) {
						LCD.clear(5);
						LCD.clear(6);
						getWPump().start();
					} else if (button == Button.ID_DOWN) {
					} else if (button == Button.ID_UP) {
						theAlarmMonitor.sendEmailAvoidingFlood("Test email with html from EV3", "Test Line1\nLine2");
					} else if (button == Button.ID_ENTER) {
						theIgnitor.stop();
						theFan.stop();
						theConveyor.testStopFeed();
						theConveyor.testStopGrating();
						getWPump().stop();
					}

					// wait Button is released
					while (Button.readButtons() != 0) {
						Delay.msDelay(5);
					}
				}
				
				Delay.msDelay(100);
			}

			getWPump().stop();
			stopAllThreads();
			LCD.clear();
		}
		
	}

	class PhasesManager {
		/**
		 *  Démarrage du feu, détecté par une augmentation significative de la température des fumées
		 *  Il y a un timeout de dureeMaxIgnitionMin
		 * @param useIgnitor
		 * @param numberInitialFeeds
		 * @param dureeMax
		 * @return
		 */
		
		private boolean runPhase2Ignition(boolean useIgnitor, int numberInitialFeeds, int dureeMax) {
			long 	startTime = System.currentTimeMillis(), 				
					lastTime = 0;

			int tempFumeeDepart, count;
			boolean start_extra_feed = false;
			Sound.beep();

			// FIXME: exit asap in case WT is zero.
			refreshTempValues();
			state = 2;
			tempFumeeDepart = tempFumee;

			log("Start Ignition phase ("+ numberInitialFeeds +" loads of  " + param_A0_sec + "sec feed ) then wait ignite for " + dureeMax + " minutes", 2);
			// theAlarmMonitor.sendEmailAvoidingFlood("Verner Starting Ignition", "Ignition for max " + dureeMax + " minutes\r\n" + getStatusString() + "Ignitor = " + useIgnitor);
			
			// No auto feed, only 9 loads of A0 sec and then 5 minutes waiting (in separate Thread)
			if (useIgnitor) {
				theIgnitor.start();
			}

			theConveyor.setTimes(0, 1);	// No auto feed
			count = 0;
			while (count++ < numberInitialFeeds && forceQuit == false && forceSkipPhase == false ) {
				theScreen.displayStatus("Igniting -" + (numberInitialFeeds - count) + ",IGN=" + (int) (useIgnitor ? 1 : 0), false);

				theConveyor.feedForSeconds(param_A0_sec);

				Delay.msDelay(3000);	// wait 3 secs
			}
			
			theFan.start();
			start_extra_feed = false;
			
			while (	tempFumee < tempFumeeDepart+param_70_deg && 
					forceQuit == false &&
					forceSkipPhase == false &&				
					waterTemp < targetWaterTemp &&
					targetWaterTemp > 0) {
				
				refreshTempValues();
				
				theScreen.showTargetWT(tempFumeeDepart+param_70_deg);

				if (	System.currentTimeMillis() - startTime >  param_TimeBeforeExtraFeed_min *60*1000 && 
						start_extra_feed==false) {
					// After (param) minutes, switch to B0 temp
					start_extra_feed = true;
					
					theConveyor.setTimes(param_B0_sec, param_FeedTempo_min - param_B0_sec);		
					log("Ignition phase at B0 tempo (" + param_B0_sec + "sec)", 2);
				}

				
				if (	System.currentTimeMillis() - startTime > dureeMax*60*1000) {
					// Timeout, augmentation temp fumées insuffisante dans le temps donné
					theScreen.clearTargetWT();
					
					if (useIgnitor == false)
						log("Ignition failed (without ignitor, so no issue here) after =" + dureeMaxIgnitionMin + "mins.", 2);
					else 
						log("Ignition failed (with ignitor) after =" + dureeMaxIgnitionMin + "mins.", 1);

					theIgnitor.stop();
					theFan.stop();

					return false;
				}		

				// Refresh countdown every 60sec
				if (System.currentTimeMillis() - lastTime > 60*1000) {
					lastTime = System.currentTimeMillis();
					theScreen.displayStatus("Ign. -" + (dureeMaxIgnitionMin - (int) ((System.currentTimeMillis() - startTime) / (60*1000))) + "m " +
							",IGN=" + (int) (useIgnitor ? 1 : 0), false);				
				}
				
				Delay.msDelay(1000);
			}

			log("Ignition ended with success in " + (System.currentTimeMillis() - startTime)/1000 + " secs", 2);
			theIgnitor.stop();

			theScreen.clearTargetWT();

			forceSkipPhase = false;		

			return true;
		}
		

		/**
		 * 
		 * @param dt	the water temp difference between target and current (so, positive if current is lower than target)
		 * @return	a factor that can be multiplied to the feeding time to slow down the approach to the target
		 */
		private float calculateMultiplier(int dt) {
			float	multiplier = 1.0f;

			// TODO: regulation should be done by slowing fan

			// pente = dy/dx = (1 - mMax) / Diff
			// m = pente * dt  + mMax
			multiplier = ((1.0f - param_MultiplierMax) / (float) param_TempDiffSlower) * dt + param_MultiplierMax;

			// Bound multiplier
			if (multiplier < 1) {
				multiplier = 1;	// No need to shorten time, only to make it longer...
			} else if (multiplier > param_MultiplierMax) {
				// Avoid too high multiplier, since there is a risk to stop the fire...
				multiplier = param_MultiplierMax;
			}

			return multiplier;
		}
		
		/**
		 *  Regime de croisière.  Will return if water temp is 10 more than the target
		 *  TODO: implement "slow" mode, where fan would be slowed down, and minimal pellet load to keep temp constant.  This should avoid many Ignitions per day.
		 */
		private void runPhase4Run() {
			long 	startTime = System.currentTimeMillis(), 
					lastTime = 0;
			float	multiplier = 1.0f;
			state = 4;

			// FIXME: cannot use setTimes in mode zero WT since it will load pellets!
			
			theConveyor.setTimes(	param_60_sec, 
					 				param_FeedTempo_min - param_60_sec);
			theFan.start();

			log("Start Run phase with feed of " + param_60_sec + " sec then wait of " + (param_FeedTempo_min - param_60_sec + "sec"), 4 );

			theAlarmMonitor.sendEmailAvoidingFlood("Verner Starting Run", 
					"Running\r\n" + 
					"Start Run phase with feed of " + param_60_sec + " sec then wait of " + (param_FeedTempo_min - param_60_sec) + "sec\r\n" +
					statusHelper.getStatusString() + "\n\n" +
					statusHelper.getLatestEventsString());

			// TODO: Smoothing the temp around target will be done by controlling the FAN speed
			// TODO: should always keep air/pellets ration stable for good combustion...
			// TODO: Mechanism to detect that fire has stopped?
			while (	waterTemp < targetWaterTemp + param_delta_temp_target_max && 
					forceSkipPhase == false &&				
					forceQuit == false &&
					targetWaterTemp > 0) {
				// Start by getting temperatures
				refreshTempValues();

				multiplier = calculateMultiplier(targetWaterTemp - waterTemp);
				
				if (waterTemp > targetWaterTemp) {
					// almost stop feeding (just enough to keep fire) but stay in Run()
					
					theConveyor.setTimesAfterWaiting(param_60_sec, 
							 (int) ((param_FeedTempo_min - param_60_sec) * multiplier));

				} else if (waterTemp > targetWaterTemp - param_TempDiffSlower){
					// Getting closer to target temp
		
					theConveyor.setTimesAfterWaiting(param_60_sec, 
							 (int) ((param_FeedTempo_min - param_60_sec) * multiplier));
				} else if ( tempFumee < waterTemp + param_TempDiffIndicatingFireStop && 
							System.currentTimeMillis() - startTime > 5 * 60 * 1000){
					// FIXME: do not run this check in the first 5 mins of run, since it may trigger when gaz temp were very low at ignition (like 20 deg), run will start with gaz temp of 50, which may be lower than water temp during a few minutes
					// Gaz temp are getting close to water temp, fire is probably gone, so better to stop!
					log("Fire is probably finished (temp lower than water + " + (param_TempDiffIndicatingFireStop) + 
							" deg) while in Run phase, so better to stop all feeding, and try to restart a fire", 1);

					break;
				} else {
					// Temp is quite lower than target, so resume normal feeding and NO Multiplier
					theConveyor.setTimesAfterWaiting(param_60_sec, 
							 param_FeedTempo_min - param_60_sec);
				}
				
				
				if (System.currentTimeMillis() - lastGratingTime > delaiInterGratingMin*60*1000) {
					lastGratingTime = System.currentTimeMillis();
					theConveyor.oneGratingCycle(true);		
				}

				// Refresh countdown every 60sec
				if (System.currentTimeMillis() - lastTime > 60*1000) {
					lastTime = System.currentTimeMillis();
					theScreen.displayStatus("Running " + ((int) ((System.currentTimeMillis() - startTime) / (60*1000))) + "m ", false);				
					log("Waiting (after feed) Multiplier for high temps= " + multiplier, 4);
				}

				Delay.msDelay(1000);
			}

			log("Stop Run phase", 4);
			theFan.stop();
			forceSkipPhase = false;
		}

		/**
		 *  Fin de cycle: brûler tout ce qui reste dans le foyer.
		 */
		private void runPhase5Stop() {
			boolean interruptAfterburn;
			long 	startTime, 
					lastTime = 0;

			state = 5;
			theScreen.displayStatus("Stop   ", false);
			log("Start stopping phase", 2);

			startTime = System.currentTimeMillis();
			
			// Stop all feeding
			theConveyor.setTimes(0, 1);

			theIgnitor.stop();	// Just in case...
			theFan.stop();

			// Clean fireplace and retract pellets the second time
			theConveyor.oneGratingCycle(true);
			Delay.msDelay(2000);
			theConveyor.oneGratingCycle(false);


			// Burn any remaining pellets
			theScreen.displayStatus("Afterburner " + param_D0_min + "mins");
			theConveyor.setTimes(0, param_D0_min * 60);
			theFan.start();

			// theAlarmMonitor.sendEmailAvoidingFlood("Verner Starting AfterBurner", "Afterbuner for " + param_D0_min + "minutes\r\n" + getStatusString() );

			interruptAfterburn = false;
			while (System.currentTimeMillis() - startTime < param_D0_min *60*1000 &&
					forceSkipPhase == false &&
					forceQuit == false && 
					interruptAfterburn == false) {

				// Start by getting temperatures
				refreshTempValues();

				if (needStartFire() == true) {
					interruptAfterburn = true;
					log("afterburner interrupted because need to restart fire asap (temp drop)", 2);		
				}

				if (System.currentTimeMillis() - startTime < (param_D0_min/2) *60*1000 &&
					tempFumee < waterTemp + param_delta_temp_target_max ) {

					interruptAfterburn = true;
					log("afterburner interrupted because gaz temp is getting close enough to water temp", 2);		
				}
								
				// Do every 60sec
				if (System.currentTimeMillis() - lastTime > 60*1000) {
					lastTime = System.currentTimeMillis();
					theScreen.displayStatus("AfterBurn -" + (param_D0_min - (int) ((System.currentTimeMillis() - startTime) / (60*1000))) + "m ", false);				

					if (waterTemp >= alarm_temp_eau-5 || tempFumee >= alarm_temp_gaz-10) {
						log("afterburner interrupted (Fan Off) because of high temps", 0);		
						theFan.stop();
					} else {
						theFan.start();
					}
				}
				
				Delay.msDelay(100);
			}
			theFan.stop();
			theConveyor.oneGratingCycle(true);

			theScreen.displayStatus("Burned (stop=" + interruptAfterburn + ")", false);

			forceSkipPhase = false;	// to avoid next phase is also skipped...
		}
		
	}
	
	public StatusHelper		statusHelper = new StatusHelper();
	public TestUtilities	testUtils = new TestUtilities();
	public PhasesManager	phasesManager = new PhasesManager();
	
	public 	static AlarmMonitor	theAlarmMonitor;
	boolean lastFiringDecision = false;

	private ArduinoI2CLink		arduinoLink;
	private AmbientTempSensor 	sondeTempFumees;
	private AmbientTempSensor 	sondeTempVis;
	private ArduinoTempSensor	sondeTempEau;
	private ContactSensor 		contacteurFinCourseGrating;

	private Conveyor		theConveyor;
	private Menu			theMenu;
	private Ignitor			theIgnitor;
	private Fan				theFan;
	private WaterPump		theWaterPump;
	private ScreenAndStats	theScreen;
	private WebServer		theWebServer;
	
	public LinkedList<LoggedEvent>		eventList;
	
	private int targetWaterTemp, 
				waterTemp, 
				tempFumee, 
				dureeMaxIgnitionMin,
				delaiInterGratingMin,
				state = 0,
				traceLevel = 2,
				startOperationsHour, stopOperationsHour;
	private long 	lastGratingTime, 
					lastStopTimeMillis;

	private Thread 	screenRefresherThread,
					conveyorThread,
					menuThread, 
					alarmThread,
					webThread;

	float samples[] =new float[1];
	private String stateName[] = { "Init", "Unused (1)", "Ignition (2)", "Firing (3)", "Running (4)", "Afterburn & Stop (5)" }; 
	private String stateColorHtml[] = { "Cyan", "Beige", "Yellow", "Orange", "Limegreen", "Beige" }; 
	private String levelColorHtml[] = { "Red", "Orange", "Limegreen", "Beige" }; 

    private BufferedWriter logBWriter, statsBWriter;
    private FileWriter logFileWriter, statsFileWriter;
    private static PrintStream pErrStream;
    private static boolean 	forceQuit = false, 
    						forceSkipPhase = false;
    
    Runtime rt;
    
	private int param_FeedTempo_min = 30,			// Feed cadence.  Wait time will be computed using this and the feed time (60 and B0)
				param_number_feeds_in_ignition = 4,	// 7 works well, but can overheat if heating is off.  5 also works (750sec to fire)
				param_number_feeds_in_re_igniting = 2,
				param_grating_sec = 5,	// Timeout of Grating in case contact sensor does not re-open 
				param_target_temp = 75, 
				param_60_sec = 3,		// feed seconds at max Output in run() mode
				param_70_deg = 30,		// Temp increase to change state from Ignition to Heating
				param_TempDiffIndicatingFireStop = param_70_deg - 5, 
				param_A0_sec = 5,		// feed seconds for initial load during Heating phase (< para_number_feeds_in_ignition> times A0 feed during 5 first minutes)
				param_B0_sec = 5,		// feed seconds during Heating phase (AFTER param_TimeBeforeExtraFeed_min minutes)
				param_D0_min = 20,		// Duration of afterburner
				alarm_Tempo_Sec = 5,	// During alarms, message will be repeat at this tempo
				alarm_temp_eau = 95,
				alarm_temp_gaz = 350,
				alarm_temp_vis = 38,	// Warning is 5 deg below  (In winter, set to 33, but 38 in Spring/Autumn)
				param_TimeBeforeExtraFeed_min = 8,
				param_TempDiffSlower = 6,		// How close to target temp does the multiplier need to start taking effect
				param_delta_temp_target_max = 10,	// How much more that target temp triggers the run() phase to stop.
				RELAY_POWER = 100;	// Critical when powering Triacs and relays
	
	/*  Un-used constants.
	private int param_80_min = 3, 		// Wait in outstanding before going to Stop state
				param_90_cdeg = 40,		// Max gaz temp in tens of deg (40=300 deg)
				param_C0 = 1,			// Minimum output (1=Pellets=35%)
				param_E0= 7,			// Ventilo Max 
				param_F0=7,
				param_G0=5,
				param_H0=2,				// Ventilo Min
				param_J0=10,			
				param_L0_min=2,			// Duration of maintenance mode
				param_MO_cdeg=15,		// temp difference between water and gaz variation to detect fire
				param_n0=7,				// Fan speed during heating up phase
				param_P0_min=40,		// Durée phase 3
				param_r0_cdeg = 3,		// Temp drop causing re-ignition during heating
				param_UO=1;				// Activation of cleanup phases
	*/
	
	private long LongOutstandingPeriod = 30*60*1000;	// 30 min outstanding means there is no chance to revive fire without Ignition
	private float param_MultiplierMax = 4.0F;
	
	/**
	 * 
	 * @return the Conveyor object, which allows to perform feed() or grating()
	 */
	public Conveyor getConveyor() {
		return theConveyor;
	}
	
	/**
	 * main method, will just loop call init() and then run(), and gently exit if the variable forceQuit is true.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		VernerCtrl vControl = new VernerCtrl();

		LCD.drawString("Verner Init", 1, 1);

		while (forceQuit == false) {
			try {
				vControl.init();
			} catch (Exception e)
			{
				vControl.log("General init() failed, Stop Program" + e, 0);
				vControl.theScreen.displayFinalErr("init failed, Stop");
												
				return;
			}

			try { 
				vControl.run();
				vControl.log("VernerController.run() method returned", 1);
				// Since this is without exception, no point of retrying, just stop
				return;
			} catch (InterruptedException e) {
				vControl.log("Exception during run() method :" + e, 0);
	        	e.printStackTrace(pErrStream);
			}

			vControl.log("Will re-initialize and restart", 1);
		}
	}
	

	/**
	 * Initializes all variables.
	 * 
	 * Conveyor on Port A	(and Grating as D)
	 * Ignition on port B
	 * Fan 		on port C
	 * 
	 * Water temp on 1
	 * Gaz temp on 2
	 * Screw temp on 3
	 * Screw contact on 4
	 */
	public void init() throws Exception {
		try {
			eventList = new LinkedList<LoggedEvent>();
			
			if (logBWriter == null) {
            	pErrStream = new PrintStream(new File("VernerOut_err.txt"));

        		logFileWriter = new FileWriter("VernerOut_" + 
        				String.format("%02d", Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) + 
        				String.format("%02d", Calendar.getInstance().get(Calendar.MONTH)+1) + 
        				Calendar.getInstance().get(Calendar.YEAR) + 
        				"_" + 
        				String.format("%02d", Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) + 
        				String.format("%02d", Calendar.getInstance().get(Calendar.MINUTE)) + 
        				".txt");
        		
        		logBWriter = new BufferedWriter(logFileWriter);
        		logBWriter.write("New log File - Initiatizing at level " + traceLevel);

        		statsFileWriter = new FileWriter("VernerOut_stats.txt", true);	// append mode
        		statsBWriter = new BufferedWriter(statsFileWriter);
        	}
        	
            logBWriter.newLine();
    		logBWriter.write("[ Time ]  State, TotMem / FreeMem (Target Temp, Water Temp, Gaz Temp, Screw Temp) / Conveyor Feed_Sec,WaitSec");
    		logBWriter.newLine();
			logBWriter.flush();

        } catch (Exception e) {
        	System.err.println("Error initiatilzing log or stat files: " + e);
        }

        try {
        	rt = Runtime.getRuntime();
        	theScreen = new ScreenAndStats(this);		// Do this early in case error messages need to be displayed.
        	
        	arduinoLink = new ArduinoI2CLink();
        	arduinoLink.initArduinoI2CLink(this, SensorPort.S1);
    		
 	
    		sondeTempEau = new ArduinoTempSensor(this, arduinoLink);
        	sondeTempFumees = new AmbientTempSensor(this, SensorPort.S2);

        	// Calib for Sensor with 15 + 10ohms
        	sondeTempFumees.calibrateLine(40, (float) 0.5829,  300,  (float) 0.0769);
        	
        	sondeTempVis = new AmbientTempSensor(this, SensorPort.S3);
        	sondeTempVis.calibrateLine(13, (float) 0.1199,  44,  (float) 0.2400);

        	contacteurFinCourseGrating = new ContactSensor(SensorPort.S4);

        	targetWaterTemp = param_target_temp;		// température cible de l'eau
        	delaiInterGratingMin = 20;
        	lastGratingTime = System.currentTimeMillis();

        	dureeMaxIgnitionMin = 20;
        	lastStopTimeMillis = 0;		// Not the time of Controller start, but far in the past to make sure Long Outstanding is detected at first run

        	/*
        	 * Relay Fan and Grating are optical ones, they react on "Backward".
        	 * Feed and Ignition are Triac-Relays, they are wired on the "Forward".
        	 */
        	theConveyor = new Conveyor(	this, 
        								new Relay(MotorPort.A, false, RELAY_POWER) , 		// Feed - Triac
        								new Relay(MotorPort.D, true, RELAY_POWER),			// Grating - Mechanical
        								contacteurFinCourseGrating, 
        								param_grating_sec);
        	conveyorThread = new Thread(theConveyor);	// Created but not started

        	theIgnitor = new Ignitor(this, new Relay(MotorPort.B, false, RELAY_POWER));		// Tria

        	theFan = new Fan(this, new Relay(MotorPort.C, true, RELAY_POWER));				// Mechanical

        	theWaterPump = new WaterPump(this, arduinoLink);
        	theWaterPump.stop();
        	
        	theMenu = new Menu(this);
        	menuThread = new Thread(theMenu); 	// Created but not started
        	
        	theAlarmMonitor = new AlarmMonitor(this, alarm_Tempo_Sec, alarm_temp_eau, alarm_temp_gaz, alarm_temp_vis);
        	alarmThread = new Thread(theAlarmMonitor);
        	
        	theWebServer = new WebServer(this);
        	
        	webThread = new Thread(theWebServer);
        	
        	forceQuit = false;
        	forceSkipPhase = false;

        	theScreen = new ScreenAndStats(this);
        	screenRefresherThread = new Thread(theScreen);  	// Created but not started
        	
        	startOperationsHour = 5;	// 6:00 AM
        	stopOperationsHour = 21;	// 22:00 PM

        } catch (Exception e) {
        	System.err.println("Error Initializing(): " + e);
        	log("Error Initializing(): " + e, 0);
        	e.printStackTrace(pErrStream);
        	
        	// Throw to upper levels to make sure program will stop
        	throw e;
        }
	}

	/**
	 * Start all background threads: Conveyor, Menu, ScreenRefresher and Alarm.
	 * @return -1 in case of error. 1 if ok.
	 */
	private int startBackgroundThreads() {
		try {
			theConveyor.pleasePrepareForRestart();
			
			log("Start Conveyor Thread", 2);
			conveyorThread.start();
			
			log("Start Menu Thread", 3);
			menuThread.start();
			
			log("Start ScreenRefresher Thread *", 3);
			screenRefresherThread.start();
			
			log("Start Web Server Thread", 3);
			webThread.start();
			// ServerRunner.run(WebServer.class);
			
			log("Start Alarm Thread", 3);
			alarmThread.start();
		} catch (Exception e)
		{
			log("Failed Starting background threads" + e, 0);
			e.printStackTrace(pErrStream);
			return -1;
		}
		
		return 1;
	}
	
	/**
	 * Request a smooth stop to all 4 main running threads.
	 */
	private void stopAllThreads() {
		theConveyor.pleaseStop();
		theMenu.pleaseStop();
		theScreen.pleaseStop();
		theAlarmMonitor.pleaseStop();

		// FIXME: stop WebServer...
	}
	
	/**
	 * Mostly called by the Menu, when user is pressing ESC to stop it all.
	 * All methods watch the forceQuit variable and will finish asap
	 */
	public void forceQuit() {
		log("Will try to force quitting the controller", 2);
		forceQuit = true;
	}
	
	/**
	 * Mostly called by the Menu, when user is pressing ESC to stop it all.
	 * All methods watch the forceQuit variable and will finish asap
	 */
	public void skipCurrentPhase() {
		log("Will try to skip this phase", 2);
		forceSkipPhase = true;
	}
	
	/**
	 * 
	 * @return the target water temp
	 */
	public int getTargetWTemp() {
		return targetWaterTemp;		
	}

	public void setTargetWTemp(int temp) {
		targetWaterTemp = temp;		
	}

	/**
	 * 
	 * @return the stored water temp (will not refresh it), use refreshTempValues() if necessary.
	 */
	public synchronized int getStoredWTemp() {
		return waterTemp;
	}

	/**
	 * 
	 * @return the stored gaz temp (will not refresh it), use refreshTempValues() if necessary.
	 */
	public int getExhTemp() {
		return tempFumee;
	}
	
	
	public WaterPump getWPump() {
		return theWaterPump;
	}

	public Fan getFan() {
		return theFan;
	}

	public Ignitor getIgnitor() {
		return theIgnitor;
	}
	
	public int getContactorValue() {
		return contacteurFinCourseGrating.getValue();
	}
	
	/**
	 * 
	 * @return the current temperature of the feeding conveyor.  
	 * A high temp (more than approx 30 deg) indicates that fire may be occuring within the conveyor screw.
	 */
	public int getScrewTemp() {
		return sondeTempVis.getValue();		
	}

	public ScreenAndStats getScreen() {
		return theScreen;
	}
	
	/**
	 * 
	 * @return true if the ignition device is currently on.
	 */
	public boolean isIgnitorOn() {
		return theIgnitor.isOn();
	}

	/**
	 * Will refresh the water and gaz value from the sensors, so that get() methods will return current temps.
	 * 
	 * Avoid using it too frequently since some Arduino methods are synchronized.
	 */
	public void refreshTempValues() {
		double tempDeg = 0;
		
		// logWithoutDetails("Refresh Temps");
		
		if (arduinoLink == null)  {
			tempDeg = -1;
		} else {
			tempDeg = sondeTempEau.getValue();
		}
		
		waterTemp = (int) tempDeg;		
		tempFumee = getExhTemp();
		
		if (sondeTempFumees == null) 
			tempFumee =-1;
		else
			tempFumee = sondeTempFumees.getValue();		
		
	}
	
	/**
	 * 
	 * @return the current state of fire: 0 is Init, 2 is ignition, 3 is Firing (Quick increase of temps), 4 is normal running, 5 is stopping/afterburner.
	 */
	public int getState() {
		return state;
	}

	public String getStateString() {
		return stateName[state];
	}

	/**
	 * Scans the entire list, per level, and deletes all old events if more than maxPerLevel
	 * Keeps the list in order.
	 * 
	 * @param maxPerLevel	Max number of events for each trace level
	 */
	public void filterEvenList(int maxPerLevel) {
		int countL[] = new int[5];
		LoggedEvent ev;
		Iterator<LoggedEvent> it = eventList.descendingIterator();
		
		while (it.hasNext()) {
			ev = it.next();
			countL[ev.level]++;
			
			if (countL[ev.level] > maxPerLevel) {
				it.remove();
			}
		}
	}
	
	/**
	 * Make sure to initialize the bWriter before logging
	 * 
	 * tracelevel: 	0 = Critical alert, or Error
	 * 				1 = Warning
	 * 				2 = important info
	 * 				3 = verbose info
	 * 				4 - Specific debug info
	 * 
	 * TODO: Record last 10 logs of each level, and display these in emails and on WebServer
	 */
	public synchronized void log(String textS, int tl) {
		LoggedEvent ev;
						
		try {
				ev = new LoggedEvent(
						statusHelper.getStatusString(),
						textS, 
						null, 
						tl);

				eventList.add(ev);
				filterEvenList(10);

				if (tl > this.traceLevel) {
					// Do not write in log file if trace-level is too high
					return;
				}
				
				logBWriter.write(ev.toString());
				logBWriter.newLine();
				logBWriter.flush();
		} catch (Exception e) {
			System.err.println("Error Logging Text (" + textS + ") " + e);
        	e.printStackTrace(pErrStream);
		}
	}

	public synchronized void logWithoutDetails(String textS, int level) {
		LoggedEvent ev;
		try {
			ev = new LoggedEvent(
					DateFormat.getTimeInstance().format(System.currentTimeMillis()),
					textS, 
					null, 
					level);

			logBWriter.write(ev.toString());
			logBWriter.newLine();
			logBWriter.flush();
		} catch (Exception e) {
			System.err.println("Error Logging Text (" + textS + ") " + e);
        	e.printStackTrace(pErrStream);
		}
	}

	public synchronized void log(String str, Exception ex) {
		try {
			log("Exception logged in .err (" + str + ") :" + ex, 0);

			// System.err.println("Logging Excepton (" + ex + ") ");
        	ex.printStackTrace(pErrStream);

		} catch (Exception e) {
			System.err.println("Error Logging Excepton (" + ex + ") " + e);
		}
	}
			
	/**
	 *  Returns true if there is need to start fire, by checking the temperatures
	 *  If targetWaterTemp is zero, it means the Burner must just wait idling. 
	 */
	 public boolean needStartFire() {
		
		if (waterTemp < (targetWaterTemp - 10) && targetWaterTemp > 0)
			return true;
		else
			return false;
	}

	 
	 /**
	  *  Returns true if current time requires to warm water (False during night) 
	  */
	 
	 public boolean timeToStartFire() {

		int nowI = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

		if (nowI < startOperationsHour || nowI >= stopOperationsHour) {
			// It night time!
			
			// Only log status changes
			if (lastFiringDecision == true) {
				log("It is night time " + nowI + " Outside of [" + startOperationsHour + " , " + stopOperationsHour + "] UTC", 3);
			}

			lastFiringDecision = false;
		} else {
			if (lastFiringDecision == false) {
				log("It is day time " + nowI + " inside of [" + startOperationsHour + " , " + stopOperationsHour + "] UTC", 3);
			}
			lastFiringDecision = true;
		}
		return lastFiringDecision;
	}
	
	 /**
	  * Principal method of this object, will decide about state changes.
	  * 
	  * @throws InterruptedException
	  */
	public void run() throws InterruptedException {
		boolean ignited = false;
		int menuChoice;
		
		refreshTempValues();
		
		lastStopTimeMillis = System.currentTimeMillis();	// To force ignition to be first tried without Ignitor
		log("Starting Menu", 2);
		int count;

		menuChoice = startMenu();
		if (menuChoice ==-1) {
			// Menu quit
			return;
		} else if (menuChoice == -2) {
			theAlarmMonitor.goSilentMode();
		}
		
		LCD.clear();
		theScreen.displayStatus("Verner Evo F&F", false);
				
		if (startBackgroundThreads() == -1) {
			// Failed, Quit
			log("Fail starting background tasks", 0);
			
			return;
		}

		log("Starting Controller + (hour = " + Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + ")", 2);
		
		if (theAlarmMonitor.sendEmailAvoidingFlood("Verner Started", "Starting " + statusHelper.getStatusString() + 
				"\n" + statusHelper.getLatestEventsString()) == false) {
			theScreen.displayStatus("Send Email Err", false);
		};

		if (RELAY_POWER!= 100) {
			log("WARNING: Power of Relays is not 100%", 0);
			theScreen.displayStatus("LOW Relay Power", true);
		}
		
		refreshTempValues();
		theConveyor.oneGratingCycle(true);	// Start by cleaning fireplace
		
		while (Button.getButtons()==0 && forceQuit == false) {
			refreshTempValues();

			if (needStartFire() && timeToStartFire() && forceQuit==false) {
				ignited = true; 	// assumption will be checked by the Ignition() method
				if (System.currentTimeMillis() - lastStopTimeMillis > LongOutstandingPeriod) {

					// Furnace has been stopped for very long, use Ignitor directly.
					log("Idle longer than " + LongOutstandingPeriod + ", so re-ignite with Ignitor", 1);
					
					ignited = phasesManager.runPhase2Ignition(true, param_number_feeds_in_ignition, dureeMaxIgnitionMin);
				} else {

					// Short enough outstanding phase, should try to re-ignite just with Fan (Only 1/4 of the time, no Ignitor)
					ignited = phasesManager.runPhase2Ignition(false, param_number_feeds_in_re_igniting, dureeMaxIgnitionMin / 4);

					if (ignited == false) {
						// Simple blowing did not work, try a proper Ignition with remainder of loads.
						log("Blowing did not re-ignite, now try with Ignitor...", 1);
						ignited = phasesManager.runPhase2Ignition(true, param_number_feeds_in_ignition - param_number_feeds_in_re_igniting, dureeMaxIgnitionMin);
					}

				}
	
				if (ignited == false) {
					// Ignition did not work, stop everything
					theAlarmMonitor.sendEmailAvoidingFlood("Ignition Failed", "Ignition phase did not succeed " + statusHelper.getStatusString() );
					stopAllThreads();

					theConveyor.setTimes(0,  1);
					theScreen.displayStatus("Ignition Failed", true);
					log("Ignition failed", 0);
					
					return;					
				}
				
				// Fire should be set now.  A way to check is that Fumes temp is well above water temp
				// TODO: is this phase really necessary? Params are so similar to Run() that this can probably be skipped
				// if (forceQuit == false)	phasesManager.runPhase3Firing();
				
				if (forceQuit == false && needStartFire())
					phasesManager.runPhase4Run();
				
				phasesManager.runPhase5Stop();
				
				lastStopTimeMillis = System.currentTimeMillis();
			} else {
				// Temp is close to the target, just do nothing since all the Stop() and Grating has been performed.
				theScreen.displayStatus("Wait t drop or day", false);
			}

			// Wait 5 min = 300s but listen to the "forcequit"
			count = 0;
			while (forceQuit == false && count++ < 300) { 
				Delay.msDelay(1000);
			}
		}

		log("VernerController Terminating - Stopping all Threads", 1);
		theScreen.displayStatus("Terminated by ESC", true);
		stopAllThreads();
	}

	
	
	/**
	 * Shows the starting menu, with a 60s timeout starting the run() method.
	 * 
	 * @return -1 if quit option has been selected.
	 */
	private int startMenu() {
		FTextMenu startMenu;	// Corrected lejos class
		int selection = 1;
		String[] menuChoice= new String[] { "Quit", "Run", "Test I/O", "Test Grating", "Test Circ", "Run no alarm"};
		
		startMenu = new FTextMenu(menuChoice, 1, "Verner Evo v1.4");
		
		while (selection != 0) {
			selection = startMenu.select(1, 60*1000);
			
			if (selection == 1 || selection == -3)
				// Go to run() in case of timeout or if "Run" selected 
				return 1;
			else if (selection == 2) {
				testUtils.test();
			}
			else if (selection == 3) {
				testUtils.testGrating();
			}
			else if (selection == 4) {
				testUtils.testCirc();
			}
			else if (selection == 5) {
				return -2;
			}
		}
		
		return -1;
	}
	

}
