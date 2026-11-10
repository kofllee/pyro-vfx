package net.kofllee.pyroui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiFontPixelSnapTest {
    @Test void headingsSnapToNearestPhysicalPixel() {
        for(float guiScale : new float[]{1f,2f,3f,4f}) {
            for(float value : new float[]{0f,.17f,10.333f,99.8f,-5.2f}) {
                float snapped=UiFontService.snapDevicePixel(value,guiScale);
                assertEquals(Math.round(value*guiScale),snapped*guiScale,0.0001);
                assertTrue(Math.abs(snapped-value)*guiScale<=.5001);
            }
        }
    }
    @Test void boldBitmapUsesIntegerDevicePixelMagnification() {
        for(float ui : new float[]{.42f,.55f,.69f,.82f,1.1f,1.37f}) {
            for(float gui : new float[]{1,2,3,4}) {
                float deviceScale=ui*gui;
                float quantized=UiFontService.quantizedHeadingScale(13f/9f,deviceScale);
                float pixelSize=quantized*deviceScale;
                assertEquals(Math.round(pixelSize),pixelSize,0.0001f);
                assertTrue(pixelSize>=0.9999f);
            }
        }
    }
}