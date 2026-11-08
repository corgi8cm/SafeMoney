package io.github.corgi8cm.safemoney.domain

import io.github.corgi8cm.safemoney.domain.SavingForm.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingFormTest {
    @Test
    fun parseAmountAcceptsOnlyPositiveIntegers() {
        assertEquals(60L, SavingForm.parseAmount(" 60 "))
        assertNull(SavingForm.parseAmount(""))
        assertNull(SavingForm.parseAmount("0"))
        assertNull(SavingForm.parseAmount("-5"))
        assertNull(SavingForm.parseAmount("1.5"))
    }

    @Test
    fun singleGivesWholeAmountToGoal() {
        assertEquals(Result.Ok(listOf(AllocationInput(7, 60))), SavingForm.single("60", 7))
    }

    @Test
    fun singleRequiresAmountAndGoal() {
        assertTrue(SavingForm.single("", 7) is Result.Error)
        assertTrue(SavingForm.single("60", null) is Result.Error)
    }

    @Test
    fun splitMatchingSumIsOk() {
        val result = SavingForm.split("100", listOf(1L to "60", 2L to "40"))
        assertEquals(Result.Ok(listOf(AllocationInput(1, 60), AllocationInput(2, 40))), result)
    }

    @Test
    fun splitReportsUnallocatedRemainder() {
        val result = SavingForm.split("100", listOf(1L to "60", 2L to "30"))
        assertEquals("分配加總 90 元與金額 100 元不符（還有 10 元沒分配）", (result as Result.Error).message)
    }

    @Test
    fun splitReportsOverAllocation() {
        val result = SavingForm.split("100", listOf(1L to "60", 2L to "50"))
        assertEquals("分配加總 110 元與金額 100 元不符（多分了 10 元）", (result as Result.Error).message)
    }

    @Test
    fun splitRequiresEveryCheckedGoalToHaveAmount() {
        assertTrue(SavingForm.split("100", listOf(1L to "100", 2L to "")) is Result.Error)
        assertTrue(SavingForm.split("100", emptyList()) is Result.Error)
    }

    @Test
    fun allocatedSumIgnoresInvalidFields() {
        assertEquals(60L, SavingForm.allocatedSum(listOf(1L to "60", 2L to "", 3L to "abc")))
    }
}
