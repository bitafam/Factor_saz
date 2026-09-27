package com.example

import com.example.util.importer.QuartzInvoiceExtractor
import com.example.util.importer.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceImportTest {

    @Test
    fun testTextNormalizerDigits() {
        val persian = "۱۲۳۴۵۶۷۸۹۰"
        assertEquals("1234567890", TextNormalizer.toEnglishDigits(persian))

        val num = TextNormalizer.parseNumber("۱۱۵,۰۰۰,۰۰۰")
        assertEquals(115000000.0, num!!, 0.01)

        val decimalSlash = TextNormalizer.parseNumber("۳/۴")
        assertEquals(3.4, decimalSlash!!, 0.01)

        val decimalDot = TextNormalizer.parseNumber(".57")
        assertEquals(0.57, decimalDot!!, 0.01)
    }

    @Test
    fun testExtractSampleDehghanPDF() {
        val sampleText = """
تاریخ: 1404/05/25
شماره:
نام حقیقی / حقوقی : اقای دهقان شماره تماس :
ردیف شرح کالا عرض cm طول m تعداد فی عرض 60 cm فی عرض ساخته شده مبلغ کل
1 صفحه عرض 60 60 4.67 1 95,000,000 95,000,000 443,650,000
2 جزیره عرض 66 66 1.03 1 95,000,000 104,500,000 107,635,000
3 سینک 35,000,000
4 هزینه کرایه 40,000,000
626,285,000
کارشناس فروش مدیر فروش
مشخصات خریدار
جمع
        """.trimIndent()

        val lines = sampleText.split("\n")
        val result = QuartzInvoiceExtractor.extractFromLines(lines, "نمونه دهقان")

        assertEquals("1404/05/25", result.invoiceDate)
        assertTrue("Buyer name should contain دهقان", result.buyerName.contains("دهقان"))
        assertEquals(2, result.normalItems.size)
        assertEquals(2, result.simpleItems.size)

        // Item 1: صفحه عرض 60, width 60, length 4.67, price60: 95,000,000
        val item1 = result.normalItems[0]
        assertTrue(item1.description.contains("صفحه"))
        assertEquals("60", item1.width)
        assertEquals("4.67", item1.length)
        assertEquals("95,000,000", item1.price60cm)

        // Item 2: جزیره عرض 66, width 66, length 1.03, price60: 95,000,000
        val item2 = result.normalItems[1]
        assertTrue(item2.description.contains("جزیره"))
        assertEquals("66", item2.width)
        assertEquals("1.03", item2.length)
        assertEquals("95,000,000", item2.price60cm)

        // Simple items: سینک 35,000,000 and هزینه کرایه 40,000,000
        assertTrue(result.simpleItems.any { it.description.contains("سینک") && it.totalAmountStr.contains("35,000,000") })
        assertTrue(result.simpleItems.any { it.description.contains("کرایه") && it.totalAmountStr.contains("40,000,000") })
    }

    @Test
    fun testExtractSample1OCR() {
        val sample1Text = """
تاریخ: 1404/11/11
شماره:
نام حقیقی: آقای کارگر شماره تماس :
ردیف شرح کالا عرض cm طول m فی عرض 60 cm فی عرض ساخته شده مبلغ کل
1 جزیره عرض90 90 3/4 115,000,000 172,500,000 586,500,000
2 جزیره عرض95 95 2/55 115,000,000 182,083,333 464,312,500
3 جزیره عرض28 28 1/6 115,000,000 53,666,667 85,866,667
4 صفحه و بین کابینتی عرض60 60 10/2 115,000,000 115,000,000 1,270,000,000
5 دیوارکوب عرض 64 64 1/7 115,000,000 122,666,667 208,400,000
6 سینک زیرکار 25,000,000 25,000,000
7 سینک کفتراش 35,000,000 35,000,000
7 سینک کفتراش 260,000,000
8 کرایه 35,000,000
2,970,079,167
کارشناس فروش مدیر فروش
کرایه و حمل طبقات به عهده مشتری میباشد
نام حقیقی/حقوقی : کایند استون شماره تماس 0
نشانی کامل :
مشخصات خریدار
جمع
توتم کوارتز . کاینداستون
مشخصات فروشنده
کانتر استون
        """.trimIndent()

        val lines = sample1Text.split("\n")
        val result = QuartzInvoiceExtractor.extractFromLines(lines, "نمونه ۱")

        assertEquals("1404/11/11", result.invoiceDate)
        assertTrue(result.buyerName.contains("کارگر"))
        assertTrue("Expected 5 normal items, got ${result.normalItems.size}", result.normalItems.size >= 5)
        assertTrue("Expected at least 3 simple items (سینک، کرایه), got ${result.simpleItems.size}", result.simpleItems.size >= 3)
    }

    @Test
    fun testExtractSample2OCR() {
        val sample2Text = """
متوت، کاینداستون، زتراوک
فاکتور فروش صفحات تنیباک کانترتاپ
خیرات: 1405/06/25 شماره:
مشخصات فروشنده
نام: دنیاک استون -متوت
نفلت:
آدرس:
مشخصات خریدار
نام خریدار: آقای ینیسح
مشخصات سنگ
دک سنگ:
نوع سنگ:
شرح لااک
فیدر شرح لااک عرض)cm )طول)m )ض
یف رع 60cm
یف عرض هتخاس شده
غلبم لک
1 جزیره هقبط فکمه عرض 80 851,000,000 230,000,000 115000000 3.7 120
2 هحفص فکمه عرض 60 580,750,000 115,000,000 115000000 5.05 60
3 هحفص و دیوار رختسا )یعیبط( 852,500,000 155,000,000 155000000 5.5 60
4 هحفص هقبط اول عرض 60 299,000,000 115,000,000 115000000 2.60 60
5 دیوارکوب عرض 70هقبط اول تمسق هود 348,833,333 134,166,666 115000000 2.60 70
6 جزیره هقبط اول عرض 92 697,666,666 268,333,333 115000000 2.60 140
7 دیوار کوب هیشاح پنجره 252,597,500 132,250,000 115000000 1.91 69
8 دیوارکوب هیشاح پنجره 83 2.4 115000000 159,083,333 381,800,000
9 دیوارکوب هیشاح پنجره 34 2.93 115000000 65,166,666 190,938,333
10 دیوارکوب هیشاح پنجره 22 2.26 115000000 42,166,666 95,296,666
11 هحفص خبطم عرض 60 687,700,000 115,000,000 115000000 5.98 60
12 دیوارکوب خبطم60 60 2.98 115000000 115,000,000 342,700,000
13 دیوارکوب عرض 70خبطم 470,925,000 134,166,666 115000000 3.51 70
14 سینک کفتراش4عدد - - - - 160,000,000
عمج لک فاکتور: 6,211,707,498 ریال
        """.trimIndent()

        val lines = sample2Text.split("\n")
        val result = QuartzInvoiceExtractor.extractFromLines(lines, "نمونه ۲")

        assertEquals("1405/06/25", result.invoiceDate)
        assertTrue("Should extract buyer name", result.buyerName.isNotBlank())
        assertTrue("Expected 13 normal items, got ${result.normalItems.size}", result.normalItems.size >= 10)
        assertTrue("Expected service item (سینک کفتراش)", result.simpleItems.any { it.description.contains("سینک") })
    }

    @Test
    fun testExtractFromMatrix() {
        val matrix = listOf(
            listOf("تاریخ: 1404/11/11", "شماره: 104"),
            listOf("مشخصات خریدار: آقای کارگر", "مشخصات فروشنده: کایند استون"),
            listOf("ردیف", "شرح کالا", "عرض cm", "طول m", "فی عرض 60 cm", "فی عرض ساخته شده", "مبلغ کل"),
            listOf("1", "جزیره عرض 90", "90", "3.4", "115,000,000", "172,500,000", "586,500,000"),
            listOf("2", "صفحه همکف عرض 60", "60", "5.05", "115,000,000", "115,000,000", "580,750,000"),
            listOf("3", "سینک کفتراش 4 عدد", "", "", "", "", "160,000,000"),
            listOf("جمع کل", "", "", "", "", "", "1,327,250,000")
        )

        val result = QuartzInvoiceExtractor.extractFromMatrix(matrix, "تست اکسل")
        assertEquals("1404/11/11", result.invoiceDate)
        assertEquals("104", result.invoiceNo)
        assertEquals("آقای کارگر", result.buyerName)
        assertEquals(2, result.normalItems.size)
        assertEquals(1, result.simpleItems.size)
        assertEquals("سینک کفتراش 4 عدد", result.simpleItems[0].description)
        assertEquals("4", result.simpleItems[0].quantityStr)
    }
}
