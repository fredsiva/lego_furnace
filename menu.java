package vernerP1;

import lejos.hardware.Button;
import lejos.hardware.lcd.LCD;
import lejos.utility.Delay;

// TODO: add rolling menu:   SkipPhase, StopAll, Feed3s, 1Grating, PumpOn, PumpOff, FanOn, FanOff, IgnOn, IgnOff


public class Menu implements Runnable {
	private VernerCtrl theController;
	private boolean pleaseStop = false;
	private String[] lineMenu= new String[] { 
				"menu L/R",		// 0 
				"SkipPhase", 	// 1
				"StopAll", 		// 2
				"Feed10s", 		// 3
				"1Grating", 	// 4
				"PumpOn", 		// 5
				"PumpOff", 		// 6
				"FanOn", 		// 7
				"FanOff", 		// 8
				"IgnOn", 		// 9
				"IgnOff" };		// 10
	
	private int lineMenuChoice = 0;
	
	public Menu(VernerCtrl aController) {
		theController = aController;
		pleaseStop = false;
	}

	public void pleaseStop() {
		pleaseStop = true;
	}
	
	public void executeMenuChoice(int theLineMenuChoice) {
		if (theLineMenuChoice == 1)
			theController.skipCurrentPhase();
		else if (theLineMenuChoice == 2)
			theController.forceQuit();
		else if (theLineMenuChoice == 3)
			theController.getConveyor().feedForSeconds(3);
		else if (theLineMenuChoice == 4)
			theController.getConveyor().oneGratingCycle(true);
		else if (theLineMenuChoice == 5)
			theController.getWPump().start();
		else if (theLineMenuChoice == 6)
			theController.getWPump().stop();
		else if (theLineMenuChoice == 7)
			theController.getFan().start();
		else if (theLineMenuChoice == 8)
			theController.getFan().stop();
		else if (theLineMenuChoice == 9)
			theController.getIgnitor().start();
		else if (theLineMenuChoice == 10)
			theController.getIgnitor().stop();
			
	}

	public void run() {

		while(pleaseStop == false) {
			
			LCD.clear(6);
			LCD.drawString(lineMenu[lineMenuChoice], 0, 6);
			
			if (Button.getButtons()==Button.ID_DOWN) {
				theController.setTargetWTemp(theController.getTargetWTemp()-1);
			} else if (Button.getButtons()==Button.ID_UP) {
				theController.setTargetWTemp(theController.getTargetWTemp()+1);
			} else if (Button.getButtons()==Button.ID_RIGHT) {
				lineMenuChoice+=1;
				if (lineMenuChoice >= lineMenu.length)
					lineMenuChoice = 0;
			} else if (Button.getButtons()==Button.ID_LEFT) {
				lineMenuChoice-=1;
				if (lineMenuChoice < 0)
					lineMenuChoice = 0;
			} else if (Button.getButtons()==Button.ID_ESCAPE) {
				theController.log("Forcing Stop phase by Menu Esc", 2);;
				theController.forceQuit();
			} else if (Button.getButtons()==Button.ID_ENTER) {
				theController.log("Line Menu Choice : " + lineMenu[lineMenuChoice], 2);
				executeMenuChoice(lineMenuChoice);
			}

			Delay.msDelay(200);
		}
		
		theController.log("Menu Stopping", 1);;	
	}
}
