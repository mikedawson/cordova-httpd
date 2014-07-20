package com.rjfun.cordova.httpd;

import java.util.Enumeration;
import java.util.Properties;
import java.util.Hashtable;
import java.io.IOException;

import android.os.Environment;

import com.rjfun.cordova.httpd.NanoHTTPD.Response;

public class WebServer extends NanoHTTPD
{
	
	/**
	 * Hashtable of mounted directories in the format of 
	 * key = prefix string (e.g. /somedir) to WebServerMountedDir 
	 * Objects
	 */
	private Hashtable mountedDirs;
	
	public WebServer(int port, AndroidFile wwwroot ) throws IOException {
		super(port, wwwroot);
		mountedDirs = new Hashtable();
		
		String sdCardPrefixString = Environment.getExternalStorageDirectory().getPath();
		//mountDir("/www1", sdCardPrefixString + "/www1");
		//mountDir("/www2", sdCardPrefixString + "/www2");
	}
	
	/**
	 * Alias a specific prefix to a specific directory
	 * 
	 * @param aliasPrefix The prefix for this e.g. /some/url/prefix
	 * @param mountDir The path to the directory root of files e.g. 
	 *  /mnt/sdcard/blah
	 */
	public void mountDir(String aliasPrefix, String mountDir) {
		AndroidFile dirFile = new AndroidFile(mountDir);
		WebServerMountedDir webMountedDir = new WebServerMountedDir(
				aliasPrefix,  dirFile, mountDir);
		mountedDirs.put(aliasPrefix, webMountedDir);
	}
	
	
	/**
	 * Serve a request.  Implementation will check if this is in one 
	 * of the mounted directories, if not will delegate to super.serve
	 * @param uri Request uri - e.g. /dir/index.html
	 * @param header request headers
	 * @param parms see parent
	 * @param files see parent
	 */
	@Override
	public Response serve( String uri, String method, Properties header, Properties parms, Properties files ){
		//see if this is a mounted directory
		Enumeration dirList = mountedDirs.keys();
		while(dirList.hasMoreElements()) {
			String dirAliasKey = dirList.nextElement().toString();
			WebServerMountedDir thisDir = (WebServerMountedDir)mountedDirs.get(dirAliasKey);
			if(uri.startsWith(thisDir.aliasPrefix)) {
				uri = uri.substring(thisDir.aliasPrefix.length());
				return super.serveFile(uri, header, thisDir.homeDir, true);
			}
		}
		
		return super.serve(uri,  method, header, parms, files);
	}
	
}

/**
 * Class to represent a mounted directory
 * 
 * @author mike
 *
 */
class WebServerMountedDir {
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


