// Created by plusminus on 20:32:01 - 27.09.2008
package org.osmdroid.views.overlay;

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.views.MapView;
import org.osmdroid.views.util.constants.OpenStreetMapViewConstants;

import android.content.Context;
import android.graphics.Canvas;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Base class representing an overlay which may be displayed on top of a {@link MapView}.
 * To add an overlay, subclass this class, create an instance, and add it to the list obtained from
 * getOverlays() of {@link MapView}.
 * 
 * This class implements a form of Gesture Handling similar to
 * {@link android.view.GestureDetector.SimpleOnGestureListener} and
 * {@link GestureDetector.OnGestureListener}. The difference is there is an additional argument for
 * the item.
 * 
 * @author Nicolas Gramlich
 */
public abstract class OpenStreetMapViewOverlay implements OpenStreetMapViewConstants {

	// ===========================================================
	// Constants
	// ===========================================================

	// ===========================================================
	// Fields
	// ===========================================================

	protected final ResourceProxy mResourceProxy;
	private boolean mVisible = true;

	// ===========================================================
	// Constructors
	// ===========================================================

	public OpenStreetMapViewOverlay(final Context ctx) {
		mResourceProxy = new DefaultResourceProxyImpl(ctx);
	}

	public OpenStreetMapViewOverlay(final ResourceProxy pResourceProxy) {
		mResourceProxy = pResourceProxy;
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	public void setVisible(final boolean pVisible) {
		this.mVisible = pVisible;
	}

	public boolean isVisible() {
		return this.mVisible;
	}

	// ===========================================================
	// Methods for SuperClass/Interfaces
	// ===========================================================

	/**
	 * Managed Draw calls gives Overlays the possibility to first draw manually and after that do a
	 * final draw. This is very useful, i sth. to be drawn needs to be <b>topmost</b>.
	 */
	public void onManagedDraw(final Canvas c, final MapView osmv) {
		if (this.mVisible) {
			onDraw(c, osmv);
			onDrawFinished(c, osmv);
		}
	}

	protected abstract void onDraw(final Canvas c, final MapView osmv);

	protected abstract void onDrawFinished(final Canvas c, final MapView osmv);

	// ===========================================================
	// Methods
	// ===========================================================

	/**
	 * By default does nothing (<code>return false</code>). If you handled the Event, return
	 * <code>true</code>, otherwise return <code>false</code>. If you returned <code>true</code>
	 * none of the following Overlays or the underlying {@link MapView} has the chance to
	 * handle this event.
	 */
	public boolean onKeyDown(final int keyCode, final KeyEvent event,
			final MapView mapView) {
		return false;
	}

	/**
	 * By default does nothing (<code>return false</code>). If you handled the Event, return
	 * <code>true</code>, otherwise return <code>false</code>. If you returned <code>true</code>
	 * none of the following Overlays or the underlying {@link MapView} has the chance to
	 * handle this event.
	 */
	public boolean onKeyUp(final int keyCode, final KeyEvent event, final MapView mapView) {
		return false;
	}

	/**
	 * <b>You can prevent all(!) other Touch-related events from happening!</b><br />
	 * By default does nothing (<code>return false</code>). If you handled the Event, return
	 * <code>true</code>, otherwise return <code>false</code>. If you returned <code>true</code>
	 * none of the following Overlays or the underlying {@link MapView} has the chance to
	 * handle this event.
	 */
	public boolean onTouchEvent(final MotionEvent event, final MapView mapView) {
		return false;
	}

	/**
	 * By default does nothing (<code>return false</code>). If you handled the Event, return
	 * <code>true</code>, otherwise return <code>false</code>. If you returned <code>true</code>
	 * none of the following Overlays or the underlying {@link MapView} has the chance to
	 * handle this event.
	 */
	public boolean onTrackballEvent(final MotionEvent event, final MapView mapView) {
		return false;
	}

	/**
	 * By default does nothing (<code>return false</code>). If you handled the Event, return
	 * <code>true</code>, otherwise return <code>false</code>. If you returned <code>true</code>
	 * none of the following Overlays or the underlying {@link MapView} has the chance to
	 * handle this event.
	 */
	public boolean onSingleTapUp(final MotionEvent e, final MapView openStreetMapView) {
		return false;
	}

	/**
	 * By default does nothing (<code>return false</code>). If you handled the Event, return
	 * <code>true</code>, otherwise return <code>false</code>. If you returned <code>true</code>
	 * none of the following Overlays or the underlying {@link MapView} has the chance to
	 * handle this event.
	 */
	public boolean onLongPress(final MotionEvent e, final MapView openStreetMapView) {
		return false;
	}

	/**
	 * By default does nothing (<code>return false</code>).
	 */
	public void onDetach(final MapView openStreetMapView) {
		return;
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

	/**
	 * Interface definition for overlays that contain items that can be snapped to (for example,
	 * when the user invokes a zoom, this could be called allowing the user to snap the zoom to an
	 * interesting point.)
	 */
	public interface Snappable {

		/**
		 * Checks to see if the given x and y are close enough to an item resulting in snapping the
		 * current action (e.g. zoom) to the item.
		 * 
		 * @param x
		 *            The x in screen coordinates.
		 * @param y
		 *            The y in screen coordinates.
		 * @param snapPoint
		 *            To be filled with the the interesting point (in screen coordinates) that is
		 *            closest to the given x and y. Can be untouched if not snapping.
		 * @param mapView
		 *            The OpenStreetMapView that is requesting the snap. Use MapView.getProjection()
		 *            to convert between on-screen pixels and latitude/longitude pairs.
		 * @return Whether or not to snap to the interesting point.
		 */
		boolean onSnapToItem(int x, int y, android.graphics.Point snapPoint,
				MapView mapView);
	}

}
