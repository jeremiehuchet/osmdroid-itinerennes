package org.osmdroid.views.overlay;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osmdroid.views.MapView;

import android.graphics.Canvas;
import android.view.KeyEvent;
import android.view.MotionEvent;

public class OverlayManager {

	final CopyOnWriteArrayList<Overlay> mOverlays;
	final CopyOnWriteArrayList<Overlay> mPermanentOverlays;

	public OverlayManager() {
		mOverlays = new CopyOnWriteArrayList<Overlay>();
		mPermanentOverlays = new CopyOnWriteArrayList<Overlay>();
	}

	public void addOverlay(Overlay overlay) {
		addOverlay(overlay, false);
	}

	public void addOverlay(Overlay overlay, boolean permanentOverlay) {
		mOverlays.add(overlay);
		if (permanentOverlay)
			mPermanentOverlays.add(overlay);
	}

	public void removeOverlay(Overlay overlay) {
		mOverlays.remove(overlay);
		mPermanentOverlays.remove(overlay);
	}

	public void clearOverlays(boolean clearPermanentOverlays) {
		if (clearPermanentOverlays)
			mOverlays.clear();
		else
			mOverlays.retainAll(mPermanentOverlays);
	}

	public Iterable<Overlay> overlays() {
		return mOverlays;
	}

	public Iterable<Overlay> overlaysReversed() {
		return new Iterable<Overlay>() {
			@Override
			public Iterator<Overlay> iterator() {
				final ListIterator<Overlay> i = mOverlays.listIterator(mOverlays.size());

				return new Iterator<Overlay>() {
					public boolean hasNext() {
						return i.hasPrevious();
					}

					public Overlay next() {
						return i.previous();
					}

					public void remove() {
						i.remove();
					}
				};
			}
		};
	}

	public int count() {
		return mOverlays.size();
	}

	public void onDraw(final Canvas c, final MapView pMapView) {
		for (Overlay overlay : mOverlays) {
			overlay.onManagedDraw(c, pMapView);
		}
	}

	public void onDetach(final MapView pMapView) {
		for (Overlay overlay : this.overlaysReversed()) {
			overlay.onDetach(pMapView);
		}
	}

	public boolean onLongPress(final MotionEvent e, final MapView pMapView) {
		for (Overlay overlay : this.overlaysReversed())
			if (overlay.onLongPress(e, pMapView))
				return true;

		return false;
	}

	public boolean onSingleTapUp(final MotionEvent e, final MapView pMapView) {
		for (Overlay overlay : this.overlaysReversed())
			if (overlay.onSingleTapUp(e, pMapView))
				return true;

		return false;
	}

	public boolean onKeyDown(final int keyCode, final KeyEvent event, final MapView pMapView) {
		for (Overlay overlay : this.overlaysReversed())
			if (overlay.onKeyDown(keyCode, event, pMapView))
				return true;

		return false;
	}

	public boolean onKeyUp(final int keyCode, final KeyEvent event, final MapView pMapView) {
		for (Overlay overlay : this.overlaysReversed())
			if (overlay.onKeyUp(keyCode, event, pMapView))
				return true;

		return false;
	}

	public boolean onTrackballEvent(final MotionEvent event, final MapView pMapView) {
		for (Overlay overlay : this.overlaysReversed())
			if (overlay.onTrackballEvent(event, pMapView))
				return true;

		return false;
	}

	public boolean onTouchEvent(final MotionEvent event, final MapView pMapView) {
		for (Overlay overlay : this.overlaysReversed())
			if (overlay.onTouchEvent(event, pMapView))
				return true;

		return false;
	}

	public boolean onDoubleTap(final MotionEvent e, final MapView pMapView) {
		for (Overlay overlay : this.overlaysReversed())
			if (overlay.onDoubleTapUp(e, pMapView))
				return true;

		return false;
	}
}
