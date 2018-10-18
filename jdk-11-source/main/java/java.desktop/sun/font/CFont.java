/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package sun.font;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

// Right now this class is final to avoid a problem with native code.
// For some reason the JNI IsInstanceOf was not working correctly
// so we are checking the class specifically. If we subclass this
// we need to modify the native code in CFontWrapper.m
public final class CFont extends PhysicalFont implements FontSubstitution {

    /* CFontStrike doesn't call these methods so they are unimplemented.
     * They are here to meet the requirements of PhysicalFont, needed
     * because a CFont can sometimes be returned where a PhysicalFont
     * is expected.
     */
    StrikeMetrics getFontMetrics(long pScalerContext) {
       throw new InternalError("Not implemented");
    }

    float getGlyphAdvance(long pScalerContext, int glyphCode) {
       throw new InternalError("Not implemented");
    }

    void getGlyphMetrics(long pScalerContext, int glyphCode,
                                  Point2D.Float metrics) {
       throw new InternalError("Not implemented");
    }

    long getGlyphImage(long pScalerContext, int glyphCode) {
       throw new InternalError("Not implemented");
    }

    Rectangle2D.Float getGlyphOutlineBounds(long pScalerContext,
                                                     int glyphCode) {
       throw new InternalError("Not implemented");
    }

    GeneralPath getGlyphOutline(long pScalerContext, int glyphCode,
                                         float x, float y) {
       throw new InternalError("Not implemented");
    }

    GeneralPath getGlyphVectorOutline(long pScalerContext,
                                               int[] glyphs, int numGlyphs,
                                               float x, float y) {
       throw new InternalError("Not implemented");
    }

    @Override
    protected long getLayoutTableCache() {
        return getLayoutTableCacheNative(getNativeFontPtr());
    }

    @Override
    protected byte[] getTableBytes(int tag) {
        return getTableBytesNative(getNativeFontPtr(), tag);
    }

    private native synchronized long getLayoutTableCacheNative(long nativeFontPtr);

    private native byte[] getTableBytesNative(long nativeFontPtr, int tag);

    private static native long createNativeFont(final String nativeFontName,
                                                final int style);
    private static native void disposeNativeFont(final long nativeFontPtr);

    private boolean isFakeItalic;
    private String nativeFontName;
    private long nativeFontPtr;

    private native float getWidthNative(final long nativeFontPtr);
    private native float getWeightNative(final long nativeFontPtr);

    private int fontWidth = -1;
    private int fontWeight = -1;

    @Override
    public int getWidth() {
        if (fontWidth == -1) {
            // Apple use a range of -1 -> +1, where 0.0 is normal
            // OpenType uses a % range from 50% -> 200% where 100% is normal
            // and maps these onto the integer values 1->9.
            // Since that is what Font2D.getWidth() expects, remap to that.
            float fw = getWidthNative(getNativeFontPtr());
            if (fw == 0.0) { // short cut the common case
                fontWidth = Font2D.FWIDTH_NORMAL;
                return fontWidth;
            }
            fw += 1.0; fw *= 100.0;
            if (fw <= 50.0) {
                fontWidth = 1;
            } else if (fw <= 62.5) {
                fontWidth = 2;
            } else if (fw <= 75.0) {
                fontWidth = 3;
            } else if (fw <= 87.5) {
                fontWidth = 4;
            } else if (fw <= 100.0) {
                fontWidth = 5;
            } else if (fw <= 112.5) {
                fontWidth = 6;
            } else if (fw <= 125.0) {
                fontWidth = 7;
            } else if (fw <= 150.0) {
                fontWidth = 8;
            } else {
                fontWidth = 9;
            }
        }
        return fontWidth;
   }

    @Override
    public int getWeight() {
        if (fontWeight == -1) {
            // Apple use a range of -1 -> +1, where 0 is medium/regular
            // Map this on to the OpenType range of 100->900 where
            // 500 is medium/regular.
            // We'll actually map to 0->1000 but that's close enough.
            float fw = getWeightNative(getNativeFontPtr());
            if (fw == 0) {
               return Font2D.FWEIGHT_NORMAL;
            }
            fw += 1.0; fw *= 500;
            fontWeight = (int)fw;
          }
          return fontWeight;
    }

    // this constructor is called from CFontWrapper.m
    public CFont(String name) {
        this(name, name);
    }

    public CFont(String name, String inFamilyName) {
        handle = new Font2DHandle(this);
        fullName = name;
        familyName = inFamilyName;
        nativeFontName = fullName;
        setStyle();
    }

    /* Called from CFontManager too */
    public CFont(CFont other, String logicalFamilyName) {
        handle = new Font2DHandle(this);
        fullName = logicalFamilyName;
        familyName = logicalFamilyName;
        nativeFontName = other.nativeFontName;
        style = other.style;
        isFakeItalic = other.isFakeItalic;
    }

    public CFont createItalicVariant() {
        CFont font = new CFont(this, familyName);
        font.nativeFontName = fullName;
        font.fullName =
            fullName + (style == Font.BOLD ? "" : "-") + "Italic-Derived";
        font.style |= Font.ITALIC;
        font.isFakeItalic = true;
        return font;
    }

    protected synchronized long getNativeFontPtr() {
        if (nativeFontPtr == 0L) {
            nativeFontPtr = createNativeFont(nativeFontName, style);
        }
        return nativeFontPtr;
    }

    private native long getCGFontPtrNative(long ptr);

    // This digs the CGFont out of the AWTFont.
    protected synchronized long getPlatformNativeFontPtr() {
        return getCGFontPtrNative(getNativeFontPtr());
    }

    static native void getCascadeList(long nativeFontPtr, ArrayList<String> listOfString);

    private CompositeFont createCompositeFont() {
        ArrayList<String> listOfString = new ArrayList<String>();
        getCascadeList(nativeFontPtr, listOfString);

        // In some italic cases the standard Mac cascade list is missing Arabic.
        listOfString.add("GeezaPro");
        FontManager fm = FontManagerFactory.getInstance();
        int numFonts = 1 + listOfString.size();
        PhysicalFont[] fonts = new PhysicalFont[numFonts];
        fonts[0] = this;
        int idx = 1;
        for (String s : listOfString) {
            if (s.equals(".AppleSymbolsFB"))  {
                // Don't know why we get the weird name above .. replace.
                s = "AppleSymbols";
            }
            Font2D f2d = fm.findFont2D(s, Font.PLAIN, FontManager.NO_FALLBACK);
            if (f2d == null || f2d == this) {
                continue;
            }
            fonts[idx++] = (PhysicalFont)f2d;
        }
        if (idx < fonts.length) {
            PhysicalFont[] orig = fonts;
            fonts = new PhysicalFont[idx];
            System.arraycopy(orig, 0, fonts, 0, idx);
        }
        CompositeFont compFont = new CompositeFont(fonts);
        compFont.mapper = new CCompositeGlyphMapper(compFont);
        return compFont;
    }

    private CompositeFont compFont;

    public CompositeFont getCompositeFont2D() {
        if (compFont == null) {
           compFont = createCompositeFont();
        }
        return compFont;
    }

    @SuppressWarnings("deprecation")
    protected synchronized void finalize() {
        if (nativeFontPtr != 0) {
            disposeNativeFont(nativeFontPtr);
        }
        nativeFontPtr = 0;
    }

    protected CharToGlyphMapper getMapper() {
        if (mapper == null) {
            mapper = new CCharToGlyphMapper(this);
        }
        return mapper;
    }

    protected FontStrike createStrike(FontStrikeDesc desc) {
        if (isFakeItalic) {
            desc = new FontStrikeDesc(desc);
            desc.glyphTx.concatenate(AffineTransform.getShearInstance(-0.2, 0));
        }
        return new CStrike(this, desc);
    }

    // <rdar://problem/5321707> sun.font.Font2D caches the last used strike,
    // but does not check if the properties of the strike match the properties
    // of the incoming java.awt.Font object (size, style, etc).
    // Simple answer: don't cache.
    private static FontRenderContext DEFAULT_FRC =
        new FontRenderContext(null, false, false);
    public FontStrike getStrike(final Font font) {
        return getStrike(font, DEFAULT_FRC);
    }

    public boolean equals(Object o) {
         if (!super.equals(o)) {
             return false;
         }

         return ((Font2D)o).getStyle() == this.getStyle();
    }

    public int hashCode() {
        return super.hashCode() ^ this.getStyle();
    }

    public String toString() {
        return "CFont { fullName: " + fullName +
            ",  familyName: " + familyName + ", style: " + style +
            " } aka: " + super.toString();
    }
}
