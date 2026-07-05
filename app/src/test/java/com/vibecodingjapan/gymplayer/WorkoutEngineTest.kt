package com.vibecodingjapan.gymplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutEngineTest {
  @Test
  fun machineDefaultsToThreeSets() {
    val machine = Machine("m1", 3, "側推胸机", "胸", "🏋")
    val state = TrainingMachineState(machine)

    assertEquals(3, state.machine.targetSets)
    assertEquals(1, state.activeSet)
  }

  @Test
  fun completedSetAdvancesHistory() {
    val machine = Machine("m1", 3, "側推胸机", "胸", "🏋")
    val set = WorkoutSet(sessionId = "s1", machineId = machine.id, machineNumber = 3, machineName = machine.name, setIndex = 1, weightKg = 40, reps = 10)
    val state = TrainingMachineState(machine, completedSets = listOf(set), activeSet = 2)

    assertEquals(1, state.completedSets.size)
    assertEquals(2, state.activeSet)
  }
}
