package com.rjfun.cordova.httpd;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.apache.http.conn.util.InetAddressUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.database.Cursor;

/**
 * This class echoes a string called from JavaScript.
 */
public class CorHttpd extends CordovaPlugin {

    /** Common tag used for logging statements. */
    private static final String LOGTAG = "CorHttpd";
    
    /** Cordova Actions. */
    private static final String ACTION_START_SERVER = "startServer";
    private static final String ACTION_STOP_SERVER = "stopServer";
    private static final String ACTION_GET_URL = "getURL";
    private static final String ACTION_GET_LOCAL_PATH = "getLocalPath";
    private static final String ACTION_MOUNT_DIR = "mountDir";
    private static final String ACTION_REGISTER_HANDLER = "registerHandler";
    private static final String ACTION_SEND_HANDLER_RESPONSE = "sendHandlerResponse";
    
    private static final int	WWW_ROOT_ARG_INDEX = 0;
    private static final int	PORT_ARG_INDEX = 1;
    
	private String localPath = "";
	private int port = 8080;

	private WebServer server = null;
	private String	url = "";

	private BroadcastReceiver downloadCompleteReceiver = null;
	private String downloadCompleteIntentName = null;
	private IntentFilter downloadCompleteIntentFilter = null;
	public long downloadAttachmentID = -1;
	
    @Override
    public boolean execute(String action, JSONArray inputs, CallbackContext callbackContext) throws JSONException {
    	final Activity cordovaAct = cordova.getActivity();
    	if(downloadCompleteReceiver == null) {
    		downloadCompleteReceiver = new DownloadCompleteReceiver(this);
    		downloadCompleteIntentName = DownloadManager.ACTION_DOWNLOAD_COMPLETE;
    		downloadCompleteIntentFilter = new IntentFilter(downloadCompleteIntentName);
    		cordova.getActivity().getApplicationContext().registerReceiver(
    				downloadCompleteReceiver, downloadCompleteIntentFilter);
    	}
    	
        PluginResult result = null;
        if (ACTION_START_SERVER.equals(action)) {
            result = startServer(inputs, callbackContext);
            
        } else if (ACTION_STOP_SERVER.equals(action)) {
            result = stopServer(inputs, callbackContext);
        } else if (ACTION_GET_URL.equals(action)) {
            result = getURL(inputs, callbackContext);            
        } else if (ACTION_GET_LOCAL_PATH.equals(action)) {
            result = getLocalPath(inputs, callbackContext);
        } else if (ACTION_MOUNT_DIR.equals(action)) {
        	result = mountDir(inputs, callbackContext);
        }else if(ACTION_REGISTER_HANDLER.equals(action)) {
        	result = registerHandler(inputs, callbackContext);
    	}else if(ACTION_SEND_HANDLER_RESPONSE.equals(action)) {
    		result = sendHandlerResponse(inputs, callbackContext);
    	}else {
            Log.d(LOGTAG, String.format("Invalid action passed: %s", action));
            result = new PluginResult(Status.INVALID_ACTION);
        }
        
        if(result != null) callbackContext.sendPluginResult( result );
        
        
                

        return true;
    }
    
    private String __getLocalIpAddress() {
    	try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (! inetAddress.isLoopbackAddress()) {
                    	String ip = inetAddress.getHostAddress();
                    	if(InetAddressUtils.isIPv4Address(ip)) {
                    		Log.w(LOGTAG, "local IP: "+ ip);
                    		return ip;
                    	}
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(LOGTAG, ex.toString());
        }
    	
		return "127.0.0.1";
    }

    private PluginResult sendHandlerResponse(JSONArray inputs, CallbackContext callbackContext) {
    	int responseId = -1;
    	String message = "";
    	try {
    		responseId = inputs.getInt(0);
    		message = inputs.getString(1);
    	}catch(JSONException e) {
    		e.printStackTrace();
    		return null;
    	}
    	
    	
    	server.sendJavascriptReponse(responseId, message);
    	return null;
    }
    
    /**
     * Register a javascript handler that will be used to generate 
     * responses for a certain prefix
     * 
     * @param inputs JSONArray where 0 = the URI prefix 
     * e.g. /some/dir/dynamic
     * 
     * @param callbackContext Cordova callback context
     * @return null - we only fire responses when requests come in
     */
    private PluginResult registerHandler(JSONArray inputs, CallbackContext callbackContext) {
    	if(server == null) {
    		callbackContext.error("Server not running");
    	}
    	
    	try {
    		String prefix = inputs.getString(0);
    		server.registerHandler(prefix, callbackContext);
    	}catch(JSONException e) {
    		callbackContext.error(e.toString());
    	}
    	
    	return null;
    }
    
    
    /**
     * Mount a specific URI prefix on the server to a specific directory
     * on the filesystem
     * 
     * @param inputs JSONArray where arg 0 = URI Prefix
     *  arg1 = FileSystem Path (/path/to/dir not file:/// etc)
     *   
     * @param callbackContext CordovaCallbackContext
     * 
     * @return null as this method directly has a call to callbackContext.success
     */
    private PluginResult mountDir(JSONArray inputs, CallbackContext callbackContext) {
    	if(server == null) {
    		callbackContext.error("Server is not running!");
    	}else {
    		try {
    			String aliasPrefix = inputs.getString(0);
    			String fileSystemPath = inputs.getString(1);
    			fileSystemPath = checkAndroidDocRootPath(fileSystemPath);
    			this.server.mountDir(aliasPrefix, fileSystemPath, cordova);
    			callbackContext.success("Mounted " + aliasPrefix 
    					+ " to " + fileSystemPath);
    		}catch(Exception e) {
    			callbackContext.error(e.toString());
    		}
    	}
    	
    	return null;
    }
    
    private String checkAndroidDocRootPath(String docRoot) {
    	String localPath = "";
    	if(docRoot.startsWith("/")) {
    		//localPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        	localPath = docRoot;
        } else {
        	//localPath = "file:///android_asset/www";
        	localPath = "www";
        	if(docRoot.length()>0) {
        		localPath += "/";
        		localPath += docRoot;
        	}
        }
    	
    	return localPath;
    }
    
    private PluginResult startServer(JSONArray inputs, CallbackContext callbackContext) {
		Log.w(LOGTAG, "startServer");
		

		final String docRoot; 
        // Get the input data.
        try {
        	docRoot = inputs.getString( WWW_ROOT_ARG_INDEX );
            port = inputs.getInt( PORT_ARG_INDEX );
        } catch (JSONException exception) {
            Log.w(LOGTAG, String.format("JSON Exception: %s", exception.getMessage()));
            callbackContext.error( exception.getMessage() );
            return null;
        }
        
        
        //function me
        if(docRoot.startsWith("/")) {
    		//localPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        	localPath = docRoot;
        } else {
        	//localPath = "file:///android_asset/www";
        	localPath = "www";
        	if(docRoot.length()>0) {
        		localPath += "/";
        		localPath += docRoot;
        	}
        }

        final CallbackContext delayCallback = callbackContext;
        cordova.getActivity().runOnUiThread(new Runnable(){
			@Override
            public void run() {
				String errmsg = __startServer();
				if(errmsg != "") {
					delayCallback.error( errmsg );
				} else {
			        url = "http://" + __getLocalIpAddress() + ":" + port;
	                delayCallback.success( url );
				}
            }
        });
        
        return null;
    }
    
    private String __startServer() {
    	String errmsg = "";
    	try {
    		AndroidFile f = new AndroidFile(localPath);
    		
	        Context ctx = cordova.getActivity().getApplicationContext();
			AssetManager am = ctx.getResources().getAssets();
    		f.setAssetManager( am );
    		
			server = new WebServer(port, f, this);
		} catch (IOException e) {
			errmsg = String.format("IO Exception: %s", e.getMessage());
			Log.w(LOGTAG, errmsg);
		}
    	return errmsg;
    }

    private void __stopServer() {
		if (server != null) {
			server.stop();
			server = null;
		}
    }
    
   private PluginResult getURL(JSONArray inputs, CallbackContext callbackContext) {
		Log.w(LOGTAG, "getURL");
		
    	callbackContext.success( this.url );
        return null;
    }

    private PluginResult getLocalPath(JSONArray inputs, CallbackContext callbackContext) {
		Log.w(LOGTAG, "getLocalPath");
		
    	callbackContext.success( this.localPath );
        return null;
    }

    private PluginResult stopServer(JSONArray inputs, CallbackContext callbackContext) {
		Log.w(LOGTAG, "stopServer");
		
        final CallbackContext delayCallback = callbackContext;
        cordova.getActivity().runOnUiThread(new Runnable(){
			@Override
            public void run() {
				__stopServer();
				url = "";
				localPath = "";
                delayCallback.success();
            }
        });
        
        return null;
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     *
     * @param multitasking		Flag indicating if multitasking is turned on for app
     */
    public void onPause(boolean multitasking) {
    	//if(! multitasking) __stopServer();
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     * @param multitasking		Flag indicating if multitasking is turned on for app
     */
    public void onResume(boolean multitasking) {
    	//if(! multitasking) __startServer();
    }

    /**
     * The final call you receive before your activity is destroyed.
     */
    public void onDestroy() {
    	__stopServer();
    }
    
    public void launchBrowser(String url, final String mimeType) {
    	System.out.println("launch browser for " + url);
    	final Activity cordovaActivity = cordova.getActivity();
    	final Context context = cordovaActivity.getApplicationContext();
    	
    	String filename = url.substring(url.lastIndexOf('/')+1);
    	
    	
    	if(mimeType != null) {
	    	DownloadManager.Request request = 
				new DownloadManager.Request(Uri.parse(url));
	    	request.setTitle(filename);
	    	request.setDescription("Course file attachment");
	    	request.setDestinationInExternalPublicDir( 
    			Environment.DIRECTORY_DOWNLOADS, filename);
	    	request.setVisibleInDownloadsUi(true);
	    	DownloadManager manager = 
				(DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
    		this.downloadAttachmentID = manager.enqueue(request);
    	}else {
    		System.out.println("Starting browser for: " + url);
    		Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    		cordovaActivity.startActivity(viewIntent);
    	}
    	
    	/*
    	try {
    		System.out.println("Attempting to start activity for " + url);
    		//cordovaActivity.startActivity(viewIntent);
    	}catch(android.content.ActivityNotFoundException e) {
    		cordovaActivity.runOnUiThread(new Runnable() {
    			public void run() {
    				Toast toast = Toast.makeText(
						cordovaActivity.getApplicationContext(), 
						"Sorry: you don't have an app to view " + mimeType, 
						Toast.LENGTH_LONG);
		    		toast.show();
    			}
    		});
    	}
    	*/
    }
}

class DownloadCompleteReceiver extends BroadcastReceiver {

	private CorHttpd parent;
	
	public DownloadCompleteReceiver(CorHttpd parent) {
		this.parent = parent;
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		long downloadID = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L);
		if(downloadID != parent.downloadAttachmentID) {
			System.out.println("ignore unrelated download");
			return;
		}
		
		DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
		DownloadManager.Query query = new DownloadManager.Query();
		query.setFilterById(downloadID);
		Cursor cursor = downloadManager.query(query);
		if(!cursor.moveToFirst()) {
			System.out.println("empty row");
			return;
		}
		
		try {
			int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
			String downloadedPackageUriString = cursor.getString(uriIndex);
			File mFile = new File(Uri.parse(downloadedPackageUriString).getPath());
			System.out.println("view file exists: " + mFile.exists());
			Intent viewIntent = new Intent(Intent.ACTION_VIEW, 
					Uri.fromFile(mFile));
			System.out.println("Requesting viewer for : " + downloadedPackageUriString);
    		parent.cordova.getActivity().startActivity(viewIntent);
		}catch(Exception e) {
			e.printStackTrace();
		}
		
		

	}
	
}