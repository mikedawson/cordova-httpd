package com.rjfun.cordova.httpd;

/**
 * This class is used to wait for the javascript to call back and set
 * the response
 * 
 * Usage:
 * 
 * In thread accepting request:
 * myRequest = new WebServerJavascriptRequest()
 * myRequest.start();
 * myRequest.wait();
 * //request is ready to serve
 * 
 * //Other thread sets response and interrupts - e.g. in callback
 * myRequest.responseStr = 'hello world';
 * myRequest.interrupt(); 
 *  
 * 
 * @author Mike Dawson <mike@ustadmobile.com>
 *
 */
public class WebServerJavascriptRequest extends Thread{
	
	public Integer id;
	
	static int idCount = 0;
	
	public static final int TIMEOUT = 2000;
	
	//We wait for this to come from javascript
	//public Response response = null;
	
	public String responseStr = "NOINFO";
	
	public WebServerJavascriptRequest() {
		setId();
		setName("WebServerRequest: " + this.id);
	}
	
	private void setId() {
		this.id = new Integer(WebServerJavascriptRequest.idCount);
		WebServerJavascriptRequest.idCount++;
	}
	
	
	public void run() {
		synchronized (this) {
			try {
				if(!this.isInterrupted()) {
					Thread.sleep(TIMEOUT);
				}
			}catch(InterruptedException e) {
				System.out.println("Interrupt received");
			}
			
			this.notifyAll();
		}
		
	}

}
	