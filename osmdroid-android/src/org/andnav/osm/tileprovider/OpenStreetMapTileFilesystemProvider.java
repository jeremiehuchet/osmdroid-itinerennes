// Created by plusminus on 21:46:41 - 25.09.2008
package org.andnav.osm.tileprovider;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.andnav.osm.tileprovider.util.StreamUtils;
import org.andnav.osm.views.util.IOpenStreetMapRendererInfo;
import org.andnav.osm.views.util.OpenStreetMapTileProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.telephony.TelephonyManager;

/**
 *
 * @author Nicolas Gramlich
 *
 */
public class OpenStreetMapTileFilesystemProvider extends OpenStreetMapAsyncTileProvider {

	// ===========================================================
	// Constants
	// ===========================================================

	private static final Logger logger = LoggerFactory.getLogger(OpenStreetMapTileFilesystemProvider.class);

	// ===========================================================
	// Fields
	// ===========================================================

	/** online provider */
	protected final OpenStreetMapTileDownloader mTileDownloader;

	private final ArrayList<ZipFile> mZipFiles = new ArrayList<ZipFile>();

	/** whether we have a data connection */
	private boolean mConnected = true;

	/** whether the sdcard is mounted read/write */
	private boolean mSdCardAvailable = true;

	/** keep around to unregister when we're done */
	private final IRegisterReceiver aRegisterReceiver;
	private final MyBroadcastReceiver mBroadcastReceiver;

	/** amount of disk space used by tile cache **/
	private static long mUsedCacheSpace;

	// ===========================================================
	// Constructors
	// ===========================================================

	/**
	 * The tiles may be found on several media.
	 * This one works with tiles stored on the file system.
	 * It and its friends are typically created and controlled by {@link OpenStreetMapTileProvider}.
	 *
	 * @param aCallback
	 * @param aRegisterReceiver
	 */
	public OpenStreetMapTileFilesystemProvider(final IOpenStreetMapTileProviderCallback aCallback, final IRegisterReceiver aRegisterReceiver) {
		super(aCallback, NUMBER_OF_TILE_FILESYSTEM_THREADS, TILE_FILESYSTEM_MAXIMUM_QUEUE_SIZE);
		this.mTileDownloader = new OpenStreetMapTileDownloader(aCallback, this);
		this.aRegisterReceiver = aRegisterReceiver;
		this.mBroadcastReceiver = new MyBroadcastReceiver();

		checkSdCard();

		findZipFiles();

		final IntentFilter networkFilter = new IntentFilter();
		networkFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		networkFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		aRegisterReceiver.registerReceiver(mBroadcastReceiver, networkFilter);

		final IntentFilter mediaFilter = new IntentFilter();
		mediaFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
		mediaFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
		mediaFilter.addDataScheme("file");
		aRegisterReceiver.registerReceiver(mBroadcastReceiver, mediaFilter);

		mUsedCacheSpace = calculateDirectorySize(TILE_PATH_BASE);
		if (mUsedCacheSpace > TILE_MAX_CACHE_SIZE_BYTES) {
			cutCurrentCache();
		}
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	/**
	 * Get the amount of disk space used by the tile cache.
	 * @return size in bytes
	 */
	public static long getUsedCacheSpace() {
		return mUsedCacheSpace;
	}

	// ===========================================================
	// Methods from SuperClass/Interfaces
	// ===========================================================

	@Override
	protected String threadGroupName() {
		return "filesystem";
	}

	@Override
	protected Runnable getTileLoader() {
		return new TileLoader();
	};

	@Override
	public void stopWorkers()
	{
		super.stopWorkers();
		this.mTileDownloader.stopWorkers();
	}

	// ===========================================================
	// Methods
	// ===========================================================

	public void detach() {
		aRegisterReceiver.unregisterReceiver(mBroadcastReceiver);
	}

	/**
	 * Get the file location for the tile.
	 * @param tile
	 * @return
	 * @throws CantContinueException if the directory containing the file doesn't exist
	 * and can't be created
	 */
	File getOutputFile(final OpenStreetMapTile tile) throws CantContinueException {
		final File file = buildFullPath(tile);
		final File parent = file.getParentFile();
		if (!parent.exists() && !createFolderAndCheckIfExists(parent)) {
			checkSdCard();
			throw new CantContinueException("Tile directory doesn't exist: " + parent);
		}
		return file;
	}

	void saveFile(final OpenStreetMapTile tile, final File outputFile, final byte[] someData) throws IOException{

		final OutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile, false), StreamUtils.IO_BUFFER_SIZE);
		bos.write(someData);
		bos.flush();
		bos.close();

		mUsedCacheSpace += someData.length; // XXX should this be synchronized? or is it a single operation?
		if (mUsedCacheSpace > TILE_MAX_CACHE_SIZE_BYTES) {
			cutCurrentCache();
		}
	}

	private File buildFullPath(final OpenStreetMapTile tile) {
		return new File(TILE_PATH_BASE, buildPath(tile) + TILE_PATH_EXTENSION);
	}

	private String buildPath(final OpenStreetMapTile tile) {
		final IOpenStreetMapRendererInfo renderer = tile.getRenderer();
		return renderer.pathBase() + "/" + tile.getZoomLevel() + "/"
					+ tile.getX() + "/" + tile.getY() + renderer.imageFilenameEnding();
	}

	private boolean createFolderAndCheckIfExists(final File aFile) {
		if (aFile.mkdirs()) {
			return true;
		}
		if (DEBUGMODE)
			logger.debug("Failed to create " + aFile + " - wait and check again");

		// if create failed, wait a bit in case another thread created it
		try {
			Thread.sleep(500);
		} catch (final InterruptedException ignore) {
		}
		// and then check again
		if (aFile.exists()) {
			if (DEBUGMODE)
				logger.debug("Seems like another thread created " + aFile);
			return true;
		} else {
			if (DEBUGMODE)
				logger.debug("File still doesn't exist: " + aFile);
			return false;
		}
	}

	private void findZipFiles() {

		mZipFiles.clear();

		final File[] z = OSMDROID_PATH.listFiles(new FileFilter() {
			@Override
			public boolean accept(final File aFile) {
				return aFile.isFile() && aFile.getName().endsWith(".zip");
			}
		});

		if (z != null) {
			for (final File file : z) {
				try {
					mZipFiles.add(new ZipFile(file));
				} catch (final Throwable e) {
					logger.warn("Error opening zip file: " + file, e);
				}
			}
		}
	}

	private List<File> getDirectoryFileList(final File aDirectory) {
		final List<File> files = new ArrayList<File>();

		final File[] z = aDirectory.listFiles();
		if (z != null) {
			for (final File file : z) {
				if (file.isFile()) {
					files.add(file);
				}
				if (file.isDirectory()) {
					files.addAll(getDirectoryFileList(file));
				}
			}
		}

		return files;
	}

	private long calculateDirectorySize(final File aDirectory) {
		long size = 0;

		final File[] z = aDirectory.listFiles();
		if (z != null) {
			for (final File file : z) {
				if (file.isFile()) {
					size += file.length();
				}
				if (file.isDirectory()) {
					size += calculateDirectorySize(file);
				}
			}
		}

		return size;
	}

	/**
	 * If the cache size is greater than the max then trim it down to the trim level.
	 * This method is synchronized so that only one thread can run it at a time.
	 */
	private void cutCurrentCache() {

		synchronized (TILE_PATH_BASE) {

			if (mUsedCacheSpace > TILE_TRIM_CACHE_SIZE_BYTES) {

				logger.info("Trimming tile cache from " + mUsedCacheSpace + " to " + TILE_TRIM_CACHE_SIZE_BYTES);

				final List<File> z = getDirectoryFileList(TILE_PATH_BASE);

				// order list by files day created from old to new
				final File[] files = z.toArray(new File[0]);
				Arrays.sort(files, new Comparator<File>(){
						@Override
						public int compare(final File f1, final File f2)
						{
							return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
						} });

				for (final File file : files) {
					if (mUsedCacheSpace <= TILE_TRIM_CACHE_SIZE_BYTES){
						break;
					}

					final long length = file.length();
					if(file.delete()) {
						mUsedCacheSpace -= length;
					}
				}
			}
		}
	}

	private synchronized InputStream fileFromZip(final OpenStreetMapTile aTile) {
		final String path = buildPath(aTile);
		for(final ZipFile zipFile : mZipFiles) {
			try {
				final ZipEntry entry = zipFile.getEntry(path);
				if (entry != null) {
					final InputStream in = zipFile.getInputStream(entry);
					return in;
				}
			} catch(final Throwable e) {
				logger.warn("Error getting zip stream: " + aTile, e);
			}
		}

		return null;
	}

	private void checkSdCard() {
		final String state = Environment.getExternalStorageState();
		logger.info("sdcard state: " + state);
		mSdCardAvailable = Environment.MEDIA_MOUNTED.equals(state);
		if (DEBUGMODE)
			logger.debug("mSdcardAvailable=" + mSdCardAvailable);
		if (!mSdCardAvailable) {
			mZipFiles.clear();
		}
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

	private class TileLoader extends OpenStreetMapAsyncTileProvider.TileLoader {

		/**
		 * The tile loading policy for deciding which file to use...
		 * The order of preferences is...
		 * prefer actual tiles over dummy tiles
		 * prefer newest tile over older
		 * prefer local tiles over zip
		 * prefer zip files in lexicographic order
		 *
		 * When a dummy tile is generated it may be constructed from
		 * coarser tiles from a lower resolution level.
		 *
		 * aTile a tile to be constructed by the method.
		 */
		@Override
		public void loadTile(final OpenStreetMapTile aTile) throws CantContinueException {

			// if there's no sdcard then don't do anything
			if (!mSdCardAvailable) {
				if (DEBUGMODE)
					logger.debug("No sdcard - do nothing for tile: " + aTile);
				tileLoaded(aTile, false);
				return;
			}

			final File tileFile = getOutputFile(aTile);



			try {
				if (tileFile.exists()) {
					if (DEBUGMODE)
						logger.debug("Loaded tile: " + aTile);
					tileLoaded(aTile, tileFile.getPath());

					// check for old tile
					final long now = System.currentTimeMillis();
					final long lastModified = tileFile.lastModified();
					if (now - lastModified > TILE_EXPIRY_TIME_MILLISECONDS) {
						// XXX perhaps we should distinguish between phone and wifi data connection
						if (mConnected && mCallback.useDataConnection()) {
							if (DEBUGMODE)
								logger.debug("Tile has expired, requesting new download: " + aTile);
							mTileDownloader.loadMapTileAsync(aTile);
						} else {
							if (DEBUGMODE)
								logger.debug("Tile has expired - not connected - not downloading: " + aTile);
						}
					}

				} else {

					if (DEBUGMODE)
						logger.debug("Tile doesn't exist: " + aTile);

					final InputStream fileFromZip = fileFromZip(aTile);
					if (fileFromZip == null) {
						// XXX perhaps we should distinguish between phone and wifi data connection
						if (mConnected && mCallback.useDataConnection()) {
							if (DEBUGMODE)
								logger.debug("Request for download: " + aTile);
							mTileDownloader.loadMapTileAsync(aTile);
						} else {
							if (DEBUGMODE)
								logger.debug("Not connected - not downloading: " + aTile);
						}

						// don't refresh the screen because there's nothing new
						tileLoaded(aTile, false);
					} else {
						if (DEBUGMODE)
							logger.debug("Use tile from zip: " + aTile);
						tileLoaded(aTile, fileFromZip);
					}
				}
			} catch (final Throwable e) {
				logger.error("Error loading tile", e);
				tileLoaded(aTile, false);
			}
		}
	}

	/**
	 * This broadcast receiver is responsible for determining the best
	 * channel over which tiles may be acquired.
	 * In other words it sets network status flags.
	 *
	 */
	private class MyBroadcastReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(final Context aContext, final Intent aIntent) {

			final String action = aIntent.getAction();
			logger.info("onReceive: " + action);

			final WifiManager wm = (WifiManager) aContext.getSystemService(Context.WIFI_SERVICE);
			final int wifiState = wm.getWifiState(); // TODO check for permission or catch error
			if (DEBUGMODE)
				logger.debug("wifi state=" + wifiState);

			final TelephonyManager tm = (TelephonyManager) aContext.getSystemService(Context.TELEPHONY_SERVICE);
			final int dataState = tm.getDataState(); // TODO check for permission or catch error
			if (DEBUGMODE)
				logger.debug("telephone data state=" + dataState);

			mConnected = wifiState == WifiManager.WIFI_STATE_ENABLED
					|| dataState == TelephonyManager.DATA_CONNECTED;

			if (DEBUGMODE)
				logger.debug("mConnected=" + mConnected);

			checkSdCard();

			if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
				findZipFiles();
			}
		}
	}

}
