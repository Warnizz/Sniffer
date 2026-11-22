package io.github.warnizz.sniffer.towers

import io.github.warnizz.sniffer.geo.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountryDataTest {
    private val header = "radio,mcc,net,area,cell,unit,lon,lat,range,samples"

    @Test fun parseCountryIndex_reads() {
        val idx = parseCountryIndex("""{"generated":"2026-11-03","countries":{"238":{"file":"238.csv.gz","bytes":1234,"rows":2}}}""")
        assertEquals("2026-11-03", idx.generated)
        assertEquals(CountryFile(238, "238.csv.gz", 1234, 2), idx.countries[238])
    }

    @Test fun parseCountryCsv_readsUnit() {
        val c = parseCountryCsv(sequenceOf(header, "LTE,238,1,1001,2000001,123,12.568,55.676,800,12")).single()
        assertEquals(Cell(Radio.LTE, 238, 1, 1001, 2000001L, 123, LatLon(55.676, 12.568), 800, 12), c)
    }

    @Test fun parseCountryCsv_emptyUnit_null() {
        assertNull(parseCountryCsv(sequenceOf("GSM,238,2,2002,31000,,12.570,55.677,1500,3")).single().unit)
    }

    @Test fun parseCountryCsv_skipsJunk() {
        val rows = parseCountryCsv(sequenceOf(
            header, "", "LTE,238", "LTE,x,1,1,1,,12,55,1,1", "LTE,238,1,1,1,,0,0,1,1", "LTE,238,1,1,1,,12,95,1,1",
            "NR,238,20,3003,68719476000,7,12.575,55.68,400,2",
        )).toList()
        assertEquals(1, rows.size)
        assertEquals(68719476000L, rows.single().cellId)
    }
}
