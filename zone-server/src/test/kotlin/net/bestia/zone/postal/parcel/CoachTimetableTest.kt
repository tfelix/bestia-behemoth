package net.bestia.zone.postal.parcel

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

class CoachTimetableTest {

  private val noon = Instant.parse("2026-11-05T12:00:00Z")

  @Test
  fun `the coach leaves on the next hour its office level sets`() {
    assertEquals(Instant.parse("2026-11-05T15:00:00Z"), CoachTimetable.nextDeparture(2, noon))
    assertEquals(Instant.parse("2026-11-05T15:00:00Z"), CoachTimetable.nextDeparture(2, noon.plusSeconds(1)))
    assertEquals(Instant.parse("2026-11-05T18:00:00Z"), CoachTimetable.nextDeparture(1, noon))
    assertEquals(Instant.parse("2026-11-05T14:00:00Z"), CoachTimetable.nextDeparture(3, noon))
  }

  @Test
  fun `the coach takes twice as long as a walking master`() {
    // 20 km: 1 h 23 min on foot, 2 h 47 min by coach, as the docs' table has it.
    val coach = CoachTimetable.coachTime(20.0)
    assertEquals(166, coach.toMinutes())
  }

  @Test
  fun `the latest arrival is the next departure plus the coach time`() {
    val arrival = CoachTimetable.latestArrival(2, 20.0, noon)

    assertEquals(Instant.parse("2026-11-05T15:00:00Z").plus(CoachTimetable.coachTime(20.0)), arrival)
    assertEquals(Duration.ofHours(3).plus(CoachTimetable.coachTime(20.0)), Duration.between(noon, arrival))
  }
}
