package net.bestia.ai.planner.goap.condition

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class BooleanConditionTest {

  private class TestBooleanCondition(name: String, state: Boolean) : BooleanCondition(name, state)

  @Test
  fun `isFulfilledBy returns false is conditions dont match`() {
    val state = TestBooleanCondition("test", true)
    val globalState = setOf(TestBooleanCondition("test", false))

    assertFalse(state.isFulfilledBy(globalState))
  }

  @Test
  fun `isFulfilledBy returns true is conditions dont match`() {
    val state = TestBooleanCondition("test", true)
    val globalState = setOf(TestBooleanCondition("test", true))

    assertTrue(state.isFulfilledBy(globalState))
  }

  @Test
  fun `isFulfilledBy returns true is conditions is matched and multiple BooleanConditions are present in global state`() {
    val state = TestBooleanCondition("a", true)
    val globalState = setOf(
        TestBooleanCondition("b", false),
        TestBooleanCondition("a", true)
    )

    assertTrue(state.isFulfilledBy(globalState))
  }

  @Test
  fun `isFulfilledBy returns false is conditions is not matched and multiple BooleanConditions are present in global state`() {
    val state = TestBooleanCondition("a", true)
    val globalState = setOf(
        TestBooleanCondition("b", true),
        TestBooleanCondition("a", false)
    )

    assertFalse(state.isFulfilledBy(globalState))
  }
}