package io.github.corgi8cm.safemoney.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AllocationsTest {
    @Test
    fun splitEvenlyGivesRemainderToFirstGoals() {
        val result = Allocations.splitEvenly(100, listOf(1L, 2L, 3L))
        assertEquals(
            listOf(AllocationInput(1, 34), AllocationInput(2, 33), AllocationInput(3, 33)),
            result,
        )
        assertEquals(100, result.sumOf { it.amount })
    }

    @Test
    fun splitEvenlyExactDivision() {
        assertEquals(
            listOf(AllocationInput(1, 50), AllocationInput(2, 50)),
            Allocations.splitEvenly(100, listOf(1L, 2L)),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun splitEvenlyRejectsTotalSmallerThanGoalCount() {
        Allocations.splitEvenly(2, listOf(1L, 2L, 3L))
    }

    @Test
    fun validateAcceptsValidSplit() {
        Allocations.validate(listOf(AllocationInput(1, 60), AllocationInput(2, 40)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateRejectsEmpty() {
        Allocations.validate(emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateRejectsZeroAmount() {
        Allocations.validate(listOf(AllocationInput(1, 0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateRejectsDuplicateGoal() {
        Allocations.validate(listOf(AllocationInput(1, 10), AllocationInput(1, 20)))
    }

    @Test
    fun fillInOrderFillsFirstGoalThenNext() {
        // 外套還差 30，記 100 → 外套 30、耳機 70
        assertEquals(
            listOf(AllocationInput(1, 30), AllocationInput(2, 70)),
            Allocations.fillInOrder(100, listOf(1L to 30L, 2L to 500L)),
        )
    }

    @Test
    fun fillInOrderStaysOnFirstGoalWhenEnough() {
        assertEquals(listOf(AllocationInput(1, 60)), Allocations.fillInOrder(60, listOf(1L to 30_000L, 2L to 500L)))
    }

    @Test
    fun fillInOrderPutsLeftoverOnLastGoal() {
        assertEquals(
            listOf(AllocationInput(1, 30), AllocationInput(2, 70)),
            Allocations.fillInOrder(100, listOf(1L to 30L, 2L to 20L)),
        )
    }

    @Test
    fun fillInOrderWithNoGoalsGivesNothing() {
        assertEquals(emptyList<AllocationInput>(), Allocations.fillInOrder(100, emptyList()))
    }
}
