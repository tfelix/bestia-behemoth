package net.bestia.ai.planner

import net.bestia.ai.Consideration
import net.bestia.ai.planner.goap.Action

interface Planner {
  fun plan(considerations: Set<Consideration>): Action?
}