/*
 * Copyright (c) 2007, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * This file has been modified from the original OpenJDK source code.
 *
 * Modifications made in 2026 by Janusz Orliński / Story about the future - Apparatus.
 */

package ext.sun.java2d.d3d;

import static sun.java2d.pipe.BufferedOpCodes.BLIT;

import java.awt.Composite;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImageOp;
import java.lang.annotation.Native;

import ext.sun.java2d.VolatileImageOp;
import ext.sun.java2d.pipe.BufferedBufImgOpsExt;
import sun.awt.image.SunVolatileImage;
import sun.java2d.ScreenUpdateManager;
import sun.java2d.SurfaceData;
import sun.java2d.d3d.D3DRenderQueue;
import sun.java2d.d3d.D3DScreenUpdateManager;
import sun.java2d.d3d.D3DSurfaceData;
import sun.java2d.pipe.BufferedContext;
import sun.java2d.pipe.Region;
import sun.java2d.pipe.RenderBuffer;
import sun.java2d.pipe.RenderQueue;

public class D3DBlitLoopsExt {
	
	private static final int USE_OPERATION = 1;
	private static final int UNUSE_OPERATION = 2;

	/**
	 * The following offsets are used to pack the parameters in
	 * createPackedParams(). (They are also used at the native level when unpacking
	 * the params.)
	 */
	@Native
	private static final int OFFSET_SRCTYPE = 16;
	@Native
	private static final int OFFSET_HINT = 8;
	@Native
	private static final int OFFSET_TEXTURE = 3;
	@Native
	private static final int OFFSET_RTT = 2;
	@Native
	private static final int OFFSET_XFORM = 1;
	@Native
	private static final int OFFSET_ISOBLIT = 0;

	//public static final int SRC_IS_OPAQUE = 1;
	
	


	/**
	 * Note: The srcImg and biop parameters are only used when invoked from the
	 * D3DBufImgOps.renderImageWithOp() method; in all other cases, this method can
	 * be called with null values for those two parameters, and they will be
	 * effectively ignored.
	 */
	public static void IsoBlit(SurfaceData srcData, SurfaceData dstData, SunVolatileImage vimg, BufferedImageOp biop, Composite comp, Region clip, AffineTransform xform, int hint, int sx1, int sy1, int sx2, int sy2,
			double dx1, double dy1, double dx2, double dy2, boolean texture) {
		int ctxflags = 0;
		if (srcData.getTransparency() == Transparency.OPAQUE) {
			ctxflags |= BufferedContext.SRC_IS_OPAQUE;
		}

		D3DSurfaceData d3dDst = (D3DSurfaceData) dstData;
		D3DRenderQueue rq = D3DRenderQueue.getInstance();
		boolean rtt = false;
		rq.lock();
		try {
			D3DSurfaceData d3dSrc = (D3DSurfaceData) srcData;
			int srctype = d3dSrc.getType();
			D3DSurfaceData srcCtxData = d3dSrc;
			if (srctype == D3DSurfaceData.TEXTURE) {
				rtt = false;
			} else {
				// the source is a backbuffer, or render-to-texture
				// surface; we set rtt to true to differentiate this kind
				// of surface from a regular texture object
				rtt = true;
			}

			BufferedContext.validateContext(srcCtxData, d3dDst, clip, comp, xform, null, null, ctxflags);
			
			boolean whitShaderHLSL = false;
			VolatileImageOp shader = null;
			boolean isAlphaPremult = d3dDst.getDeviceConfiguration().getColorModel().isAlphaPremultiplied();
			

			if (biop != null) {
				if (whitShaderHLSL = (biop instanceof VolatileImageOp)) {
					shader = (VolatileImageOp) biop;
					int state = USE_OPERATION;
					rq.flushAndInvokeNow(shader.setupForRunAndGet(state, isAlphaPremult, false));
				} else {
					BufferedBufImgOpsExt.enableBufImgOp(rq, d3dSrc, vimg, biop); // TODO: odkomentować, bo to tylko dla testów
				}
			}
			

			int packedParams = createPackedParams(true, texture, rtt, xform != null, hint, 0 /* unused */);
			enqueueBlit(rq, srcData, dstData, packedParams, sx1, sy1, sx2, sy2, dx1, dy1, dx2, dy2);

			if (whitShaderHLSL) {
				int state = UNUSE_OPERATION;
				rq.flushAndInvokeNow(shader.setupForRunAndGet(state, isAlphaPremult, false));
			} else if (biop != null){
				BufferedBufImgOpsExt.disableBufImgOp(rq, biop);
			}
			
		} finally {
			rq.unlock();
		}

		if (rtt && (d3dDst.getType() == D3DSurfaceData.WINDOW)) {
			// we only have to flush immediately when copying from a
			// (non-texture) surface to the screen; otherwise Swing apps
			// might appear unresponsive until the auto-flush completes
			D3DScreenUpdateManager mgr = (D3DScreenUpdateManager) ScreenUpdateManager.getInstance();
			mgr.runUpdateNow();
		}
	}

	/**
	 * Packs the given parameters into a single int value in order to save space on
	 * the rendering queue.
	 */
	private static int createPackedParams(boolean isoblit, boolean texture, boolean rtt, boolean xform, int hint, int srctype) {
		return ((srctype << OFFSET_SRCTYPE) | (hint << OFFSET_HINT) | ((texture ? 1 : 0) << OFFSET_TEXTURE) | ((rtt ? 1 : 0) << OFFSET_RTT) | ((xform ? 1 : 0) << OFFSET_XFORM) | ((isoblit ? 1 : 0) << OFFSET_ISOBLIT));
	}

	/**
	 * Enqueues a BLIT operation with the given parameters. Note that the
	 * RenderQueue lock must be held before calling this method.
	 */
	private static void enqueueBlit(RenderQueue rq, SurfaceData src, SurfaceData dst, int packedParams, int sx1, int sy1, int sx2, int sy2, double dx1, double dy1, double dx2, double dy2) {
		// assert rq.lock.isHeldByCurrentThread();
		RenderBuffer buf = rq.getBuffer();
		rq.ensureCapacityAndAlignment(72, 24);
		buf.putInt(BLIT);
		buf.putInt(packedParams);
		buf.putInt(sx1).putInt(sy1);
		buf.putInt(sx2).putInt(sy2);
		buf.putDouble(dx1).putDouble(dy1);
		buf.putDouble(dx2).putDouble(dy2);
		buf.putLong(src.getNativeOps());
		buf.putLong(dst.getNativeOps());
	}

}
