package com.rjfun.cordova.httpd;

import java.util.Enumeration;
import java.util.Properties;
import java.util.Hashtable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import com.rjfun.cordova.httpd.WebServerJavascriptRequest;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;

import com.rjfun.cordova.httpd.NanoHTTPD.Response;

/**
 * Class extends NanoHTTPD to add functionality for control from the 
 * Cordova plugin
 *
 */
public class WebServer extends NanoHTTPD
{
	
	/**
	 * Hashtable of mounted directories in the format of 
	 * key = prefix string (e.g. /somedir) to WebServerMountedDir 
	 * Objects
	 */
	private Hashtable mountedDirs;
	
	/**
	 * Registry of paths for which there is a callback handler
	 */
	private Hashtable callbackHandlers;
	
	/**
	 * Registry of pending javascript requests
	 */
	private Hashtable pendingJavascriptRequests;
	
	
	private CorHttpd corHttpd;
	
	private int serverPort;
	
	
	/**
	 * Contructor for web server object
	 * 
	 * @param port Numerical port to operate on
	 * @param wwwroot AndroidFile object representing the default root 
	 * directory
	 * 
	 * @throws IOException
	 */
	public WebServer(int port, AndroidFile wwwroot, CorHttpd corHttpd) throws IOException {
		super(port, wwwroot);
		this.serverPort = port;
		mountedDirs = new Hashtable();
		callbackHandlers = new Hashtable();
		pendingJavascriptRequests = new Hashtable();
		this.corHttpd = corHttpd;
	}
	
	/**
	 * Alias a specific url prefix to a specific file directory 
	 * 
	 * @param aliasPrefix The url prefix for this e.g. /some/url/prefix
	 * @param mountDir The path to the directory root of files e.g. 
	 *  /mnt/sdcard/blah
	 */
	public void mountDir(String aliasPrefix, String mountDir, CordovaInterface cordova) {
		AndroidFile dirFile = new AndroidFile(mountDir);
		
		if(!mountDir.startsWith("/")) {
			Context ctx = cordova.getActivity().getApplicationContext();
			AssetManager am = ctx.getResources().getAssets();
			dirFile.setAssetManager( am );
		}
		
		WebServerMountedDir webMountedDir = new WebServerMountedDir(
				aliasPrefix,  dirFile, mountDir);
		mountedDirs.put(aliasPrefix, webMountedDir);
	}
	
	/**
	 * Register a Javascript callback handler to be used to generate
	 * responses for a specific url prefix.  When a request matches
	 * this prefix the CallbackContext will be stored so it can be used
	 * when a request matches the prefix given.
	 * 
	 * @param prefix the url prefix e.g. /dynamic/foo
	 * @param callbackContext Cordova callback context object
	 */
	public void registerHandler(String prefix, CallbackContext callbackContext) {
		WebServerMountedHandler handler = new WebServerMountedHandler(prefix, callbackContext);
		callbackHandlers.put(prefix, handler);
	}
	
	/**
	 * Utility function for looking through hashtables given a url
	 * prefix - returns the first match
	 * 
	 * @param uri Request URI given
	 * @param handlerTable Hashtable with keys as strings of URL prefixes
	 * 
	 * @return first matching object
	 */
	public Object getMatchForURI(String uri, Hashtable handlerTable) {
		Enumeration dirList = handlerTable.keys();
		while(dirList.hasMoreElements()) {
			String currentPrefix = dirList.nextElement().toString();
			if(uri.startsWith(currentPrefix)) {
				return handlerTable.get(currentPrefix);
			}
		}
		
		return null;
	}
	
	/**
	 * Used by callback to send the response for a given request we are
	 * waiting for
	 * 
	 * @param requestId ID of the request being sent
	 * @param message Body that will be used for response
	 */
	public void sendJavascriptReponse(int requestId, String message) {
		Object reqObj = pendingJavascriptRequests.get(new Integer(requestId));
		if(reqObj != null) {
			WebServerJavascriptRequest req = 
					(WebServerJavascriptRequest)reqObj;
			req.responseStr = message;
			req.interrupt();
		}
	}
	
	/**
	 * Serve a request.  Implementation will check if this is in one 
	 * of the mounted directories, or if a javascript response handler
	 * is active.  Otherwise will fallback to super.serve
	 * 
	 * @param uri Request uri - e.g. /dir/index.html
	 * @param header request headers
	 * @param parms see parent
	 * @param files see parent
	 */
	@Override
	public Response serve( String uri, String method, Properties header, Properties parms, Properties files ){
		//see if this is a mounted directory
		System.out.println("Serve uri: " + uri);
		Object mountedDirObj = getMatchForURI(uri, mountedDirs);
		if(mountedDirObj != null) {
			WebServerMountedDir thisDir = 
					(WebServerMountedDir)mountedDirObj;
			uri = uri.substring(thisDir.aliasPrefix.length());
			if(parms.containsKey("startdownload")) {
				String fullURL = "http://127.0.0.1:" + this.serverPort + uri;
				int dotPos = fullURL.lastIndexOf(".");
				System.out.println("startdownload: looking at " + fullURL);
				String mimeType = null;
				if(dotPos != -1 && dotPos < fullURL.length() - 2) {
					System.out.println("startdownload: finding extension" + fullURL);
					String extension = fullURL.substring(dotPos+1);
					System.out.println("startdownload: extension is " + extension);
					mimeType = NanoHTTPD.getMimeType(extension);
					System.out.println("startdownload: mime type is " + mimeType);
				}
				this.corHttpd.launchBrowser(fullURL, mimeType);
			}else if(uri.endsWith(".xhtml")){
				Response resp = null;
				try {
					String fileContents = null;
					File f = new File(thisDir.homeDir, uri);
					InputStream fin = new FileInputStream(f);
					ByteArrayOutputStream bout = new ByteArrayOutputStream();
					byte[] buf = new byte[1024];
					int bytesRead;
					while((bytesRead = fin.read(buf))!= -1) {
						bout.write(buf, 0, bytesRead);
					}
					fin.close();
					String contentStr = new String(bout.toByteArray(), "UTF-8");
					contentStr = contentStr.replaceAll("&\\s", "&amp;");
					resp = new Response(NanoHTTPD.HTTP_OK, 
							NanoHTTPD.MIME_HTML, contentStr);					
				}catch(IOException e) {
					resp = new Response(NanoHTTPD.HTTP_INTERNALERROR, NanoHTTPD.MIME_HTML, "Error: " + e.toString());
				}
				return resp;
			}else {
				return super.serveFile(uri, header, thisDir.homeDir, true);
			}
		}
		
		Object callbackHandlerObj = getMatchForURI(uri, callbackHandlers);
		if(callbackHandlerObj != null) {
			WebServerMountedHandler handler = 
					(WebServerMountedHandler)callbackHandlerObj;
			
			WebServerJavascriptRequest req = new WebServerJavascriptRequest();
			pendingJavascriptRequests.put(req.id, req);
			req.start();
			
			JSONArray args = new JSONArray();
			try {
				args.put(0, req.id);
				args.put(1, uri);
				Enumeration e = parms.propertyNames();
				JSONObject parmsObj = new JSONObject();
				
				while(e.hasMoreElements()) {
				    String paramName = (String)e.nextElement();
				    parmsObj.put(paramName, parms.getProperty(paramName));
				}
				args.put(2, parmsObj);
			}catch(JSONException e) {
				e.printStackTrace();
			}
			
			
			PluginResult res = new PluginResult(Status.OK, args);
			res.setKeepCallback(true);
			handler.callbackContext.sendPluginResult(res);
			
			System.out.println("Waiting for response to " + req.id);
			
			synchronized(req) {
				try {req.wait();}
				catch(InterruptedException e) {}
			}
			
			//wait for the call to come back
			//Create the lock object, call wait for on it.
			
			Response response = new Response(NanoHTTPD.HTTP_OK, 
					NanoHTTPD.MIME_HTML, "Got " + req.responseStr);
			pendingJavascriptRequests.remove(req.id);
			return response;
		}
		
		
		return super.serve(uri,  method, header, parms, files);
	}
	
	/**
	 * Class to represent a mounted directory on the server where 
	 * requests will be served from a different directory    
	 * 
	 * @author mike
	 *
	 */
	public class WebServerMountedDir {
		/** Alias prefix on web server e.g. /some/path */
		public String aliasPrefix;
		
		/** AndroidFile object for the root directory we are serving from */
		public AndroidFile homeDir;
		
		/** Path of the directory on the filesystem - match to homeDir */
		public String dirPath;
		
		/**
		 * Constructor
		 * 
		 * @param aliasPrefix Alias prefix on web server e.g. /some/path
		 * @param homeDir AndroidFile object for the root directory we are serving from
		 * @param dirPath Path of the directory on the filesystem  - match to homeDir
		 */
		public WebServerMountedDir(String aliasPrefix, AndroidFile homeDir, String dirPath) {
			this.aliasPrefix = aliasPrefix;
			this.homeDir = homeDir;
			this.dirPath = dirPath;
		}
		
	}


	/**
	 * Class to represent a directory where contents will be generated 
	 * by javascript callback
	 * 
	 * @author Mike Dawson
	 *
	 */
	public class WebServerMountedHandler {
		
		/** The URI alias prefix - e.g. /some/dir/dynamic */
		public String aliasPrefix;
		
		/** Cordova CallbackContext used to send pluginresult messages */
		public CallbackContext callbackContext;
		
		
		WebServerMountedHandler(String aliasPrefix, CallbackContext callbackContext) {
			this.aliasPrefix = aliasPrefix;
			this.callbackContext = callbackContext;
		}
		
	}

}
