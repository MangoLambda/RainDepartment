package com.raindepartment.weather

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardDataTest {
    @Test
    fun localeDefaultsUseImperialForUsDevices() {
        assertEquals(UnitSystem.IMPERIAL, defaultUnitSystem(Locale.US))
        assertEquals(UnitSystem.METRIC, defaultUnitSystem(Locale.UK))
    }

    @Test
    fun dashboardTemperatureConvertsBetweenSystems() {
        val forecast = MockDashboardData.current

        assertEquals("84°", forecast.temperature(forecast.currentFahrenheit, UnitSystem.IMPERIAL))
        assertEquals("29°", forecast.temperature(forecast.currentFahrenheit, UnitSystem.METRIC))
        assertEquals("0.68 in", forecast.precipitation(0.68, UnitSystem.IMPERIAL))
        assertEquals("17.3 mm", forecast.precipitation(0.68, UnitSystem.METRIC))
    }

    @Test
    fun mockForecastContainsReferenceHourlyShape() {
        val hourly = MockDashboardData.current.hourly

        assertEquals(8, hourly.size)
        assertEquals("Now", hourly.first().time)
        assertEquals(90, hourly[4].precipitationChance)
        assertEquals(15, hourly[4].windMph)
    }
}
