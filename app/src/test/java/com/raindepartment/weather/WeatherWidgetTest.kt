package com.raindepartment.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherWidgetTest {
    @Test
    fun locationLabelSplitsCityAndRegion() {
        assertEquals("Sherbrooke\nQuebec", widgetLocationLabel("Sherbrooke, Quebec"))
    }

    @Test
    fun locationLabelTruncatesLongLines() {
        assertEquals("San Franc…\nBritish C…", widgetLocationLabel("San Francisco, British Columbia"))
    }
}
