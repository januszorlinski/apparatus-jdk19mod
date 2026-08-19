/*
 * Copyright (c) 2026, Janusz Orliński / Story about the future - Apparatus.
 *
 * This file is licensed under the GNU General Public License
 * version 2, with the Classpath Exception.
 */

package ext.sun.java2d;

import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorModel;

public interface VolatileImageOp extends BufferedImageOp, Runnable {
	
	public static final int USE_OPERATION = 1;
	public static final int UNUSE_OPERATION = 2;
	
	public static final int GL_TEXTURE_2D = 0xde1;
	
	public Runnable setupForRunAndGet(int state, boolean isAlphaPremult, boolean isOGLTexture2D);

	// ------------ BufferedImageOp implementations ------------------
	default public BufferedImage filter(BufferedImage src, BufferedImage dest) {
		throw new UnsupportedOperationException("Operation not avalible for VolatileImageOp.");
	}

	default public Rectangle2D getBounds2D(BufferedImage src) {
		throw new UnsupportedOperationException("Operation not avalible for VolatileImageOp.");
	}

	default public BufferedImage createCompatibleDestImage(BufferedImage src, ColorModel destCM) {
		throw new UnsupportedOperationException("Operation not avalible for VolatileImageOp.");
	}

	default public Point2D getPoint2D(Point2D srcPt, Point2D dstPt) {
		throw new UnsupportedOperationException("Operation not avalible for VolatileImageOp.");
	}

	default public RenderingHints getRenderingHints() {
		throw new UnsupportedOperationException("Operation not avalible for VolatileImageOp.");
	}

}
