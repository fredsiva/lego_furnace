package vernerP1;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

public class WebServer extends NanoHTTPD implements Runnable {
	public VernerCtrl theController;

	public WebServer(VernerCtrl aController) {
        super(80, aController);
		theController = aController;
    }

	public void run() {
		try {
			super.start();
		} catch (IOException ex) {
			theController.log("Exception starting the Webserver", ex);;
		}
	}
	
    @Override public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();
        
        // theController.log("" + method + " '" + uri + "' ", 3);
        
        Map<String, String> header = session.getHeaders();
        Map<String, String> parms = session.getParms();

        Iterator<String> e = header.keySet().iterator();
        while (e.hasNext()) {
            String value = e.next();
            // theController.log("  HDR: '" + value + "' = '" + header.get(value) + "'", 3);
        }
        e = parms.keySet().iterator();
        
        // theController.log(parms.keySet().size() + " parameters specified", 3);
        
        while (e.hasNext()) {
            String value = e.next();
            // theController.log("  PRM: '" + value + "' = '" + parms.get(value) + "'", 3);
        }

        StringBuilder msg = new StringBuilder(
        		"<html><head><title>Verner EVO F&F</title>" + 
        				// "<meta http-equiv=\"refresh\" content=\"60\">" + 
        		"</head>" + 
        		"<body><h1>EV3 Server <br/>" + 
        		theController.statusHelper.getVerboseStatusHtmlString() + 
        		"</h1><br/>");

        msg.append("<form action='?' method='get'>\n");
        msg.append("<p>Set Target Temp (or WPON WPOFF): <input type='text' name='cmd'></p>\n");
        msg.append("</form>\n");
        msg.append("</body></html>\n");

        if (parms.get("cmd") != null) {
        	theController.log("Web Command Received: " + parms.get("cmd"), 2);
        	
        	if (parms.get("cmd").equals("WPON")) {
				theController.getWPump().start();
        		
        	} else if (parms.get("cmd").equals("WPOFF")) {
    				theController.getWPump().stop();            		
            } else {
	        	int newTemp = Integer.parseInt(parms.get("cmd"));
	        	
	        	if (newTemp >= 0 && newTemp < 85)
	        		theController.setTargetWTemp(newTemp);
        	}
        }

        return new NanoHTTPD.Response(msg.toString());
    }
}
