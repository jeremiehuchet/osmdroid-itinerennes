// Created by plusminus on 21:46:41 - 25.09.2008
package org.osmdroid.tileprovider.modules;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

import org.osmdroid.tileprovider.IRegisterReceiver;
import org.osmdroid.tileprovider.MapTile;
import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.tileprovider.MapTileRequestState;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.graphics.drawable.Drawable;

/**
 * A tile provider that can serve tiles from an archive using the supplied tile source. The tile
 * provider will automatically find existing archives and use each one that it finds.
 *
 * @author Marc Kurtz
 * @author Nicolas Gramlich
 *
 */
public class MapTileFileArchiveProvider extends MapTileFileStorageProviderBase {

	// ===========================================================
	// Constants
	// ===========================================================

	private static final Logger logger = LoggerFactory.getLogger(MapTileFileArchiveProvider.class);

	// ===========================================================
	// Fields
	// ===========================================================

	private final ArrayList<IArchiveFile> mArchiveFiles = new ArrayList<IArchiveFile>();

	private ITileSource mTileSource;

	// ===========================================================
	// Constructors
	// ===========================================================

	/**
	 * The tiles may be found on several media. This one works with tiles stored on the file system.
	 * It and its friends are typically created and controlled by
	 * {@link MapTileProviderBase}.
	 *
	 * @param aCallback
	 * @param aRegisterReceiver
	 */
	public MapTileFileArchiveProvider(final ITileSource pTileSource,
			final IRegisterReceiver pRegisterReceiver) {
		super(NUMBER_OF_TILE_FILESYSTEM_THREADS, TILE_FILESYSTEM_MAXIMUM_QUEUE_SIZE,
				pRegisterReceiver);

		mTileSource = pTileSource;

		findArchiveFiles();
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Methods from SuperClass/Interfaces
	// ===========================================================

	@Override
	public boolean getUsesDataConnection() {
		return false;
	}

	@Override
	protected String getName() {
		return "File Archive Provider";
	}

	@Override
	protected String getThreadGroupName() {
		return "filearchive";
	}

	@Override
	protected Runnable getTileLoader() {
		return new TileLoader();
	}

	@Override
	public int getMinimumZoomLevel() {
		return mTileSource != null ? mTileSource.getMinimumZoomLevel() : Integer.MAX_VALUE;
	}

	@Override
	public int getMaximumZoomLevel() {
		return mTileSource != null ? mTileSource.getMaximumZoomLevel() : Integer.MIN_VALUE;
	}

	@Override
	protected void onMediaMounted() {
		findArchiveFiles();
	}

	@Override
	protected void onMediaUnmounted() {
		findArchiveFiles();
	}

	@Override
	public void setTileSource(final ITileSource pTileSource) {
		mTileSource = pTileSource;
	}

	// ===========================================================
	// Methods
	// ===========================================================

	private void findArchiveFiles() {

		mArchiveFiles.clear();

		if (!getSdCardAvailable()) {
			return;
		}

		// path should be optionally configurable
		final File[] z = OSMDROID_PATH.listFiles();
		for (final File file : z) {
			final IArchiveFile archiveFile = ArchiveFileFactory.getArchiveFile(file);
			if (archiveFile != null) {
				mArchiveFiles.add(archiveFile);
			}
		}
	}

	private synchronized InputStream getInputStream(final MapTile pTile) {
		for (final IArchiveFile archiveFile : mArchiveFiles) {
			final InputStream in = archiveFile.getInputStream(mTileSource, pTile);
			if (in != null) {
				if(DEBUGMODE) {
					logger.debug("Found tile " + pTile + " in " + archiveFile);
				}
				return in;
			}
		}

		return null;
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

	private class TileLoader extends MapTileModuleProviderBase.TileLoader {

		@Override
		public Drawable loadTile(final MapTileRequestState pState) {

			if (mTileSource == null) {
				return null;
			}

			final MapTile pTile = pState.getMapTile();

			// if there's no sdcard then don't do anything
			if (!getSdCardAvailable()) {
				if (DEBUGMODE) {
					logger.debug("No sdcard - do nothing for tile: " + pTile);
				}
				return null;
			}

			InputStream inputStream = null;
			try {
				if (DEBUGMODE) {
					logger.debug("Tile doesn't exist: " + pTile);
				}

				inputStream = getInputStream(pTile);
				if (inputStream != null) {
					if (DEBUGMODE) {
						logger.debug("Use tile from archive: " + pTile);
					}
					final Drawable drawable = mTileSource.getDrawable(inputStream);
					return drawable;
				}
			} catch (final Throwable e) {
				logger.error("Error loading tile", e);
			} finally {
				if (inputStream != null) {
					StreamUtils.closeStream(inputStream);
				}
			}

			return null;
		}
	}
}
