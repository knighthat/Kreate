package app.kreate.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration


class DurationUtlTest {

    @Test
    fun emptyStringToDuration() {
        val actual = "".toDuration()
        assertEquals(Duration.ZERO, actual )
    }

    @Test
    fun onlyDigitsToDuration() {
        val subj = arrayOf(
            29, 769, 797, 25, 742, 862, 213, 975, 479, 663, 938, 586, 906, 931, 560, 1, 645, 55, 126, 741,
            883, 663, 866, 441, 107, 98, 131, 789, 712, 661, 434, 560, 45, 350, 638, 671, 210, 566, 958, 51,
            473, 543, 283, 996, 362, 644, 925, 333, 231, 253, 505, 153, 760, 172, 904, 840, 697, 728, 167, 483,
            382, 426, 485, 279, 467, 119, 509, 432, 960, 261, 713, 400, 775, 119, 648, 359, 564, 472, 684, 467,
            155, 406, 85, 753, 624, 518, 180, 63, 972, 687, 206, 201, 284, 271, 402, 704, 345, 175, 142, 92,
            75, 687, 549, 986, 437, 320, 976, 31, 355, 703, 860, 83, 95, 20, 825, 17, 549, 100, 951, 677,
            976, 645, 924, 677, 741, 739, 336, 690, 491, 516, 552, 952, 944, 521, 126, 748, 369, 581, 500, 608,
            510, 486, 433, 89, 728, 896, 846, 988, 779, 912, 298, 661, 902, 629, 681, 205, 746, 360, 801, 557,
            494, 878, 109, 303, 762, 331, 291, 204, 135, 322, 898, 454, 593, 834, 789, 155, 355, 461, 465, 474,
            81, 581, 763, 672, 710, 428, 951, 182, 114, 23, 999, 204, 576, 635, 897, 604, 476, 96, 910, 978
        )

        for( num in subj ) {
            val expected = num.toDuration( DurationUnit.SECONDS )
            val actual = num.toString().toDuration()
            assertEquals( expected, actual )
        }
    }

    @Test
    fun invalidSecondRangeToDuration() {
        val subj = arrayOf(
            "00:70", "00:100", "00:60", "00:70", "00:96", "00:79", "00:65", "00:98", "00:99", "00:75"
        )
        for( dur in subj )
            assertEquals(Duration.ZERO, dur.toDuration() )
    }

    @Test
    fun invalidSecondNonDigitToDuration() {
        val subj = arrayOf(
            "74:18:26:x", "100:7:49:x", "66:19:50:x", "9:17:12:x", "66:4:32:x",
            "70:22:19:x", "19:19:12:x", "21:13:51:x", "78:13:56:x", "95:13:49:x"
        )
        for( dur in subj )
            assertEquals(Duration.ZERO, dur.toDuration() )
    }

    @Test
    fun invalidMinuteRangeToDuration() {
        val subj = arrayOf(
            "63:18", "86:2", "78:55", "99:8", "87:35", "66:41", "61:42", "63:38", "60:42", "89:18"
        )
        for( dur in subj )
            assertEquals(Duration.ZERO, dur.toDuration() )
    }

    @Test
    fun invalidMinuteNonDigitToDuration() {
        val subj = arrayOf(
            "6:10:x:38", "63:15:x:35", "14:18:x:57", "69:21:x:22", "66:23:x:9",
            "24:12:x:32", "38:7:x:49", "93:9:x:37", "100:8:x:19", "27:15:x:50"
        )
        for( dur in subj )
            assertEquals(Duration.ZERO, dur.toDuration() )
    }

    @Test
    fun invalidHourNonDigitToDuration() {
        val subj = arrayOf(
            "6:10:x:38", "63:15:x:35", "14:18:x:57", "69:21:x:22", "66:23:x:9",
            "24:12:x:32", "38:7:x:49", "93:9:x:37", "100:8:x:19", "27:15:x:50"
        )
        for( dur in subj )
            assertEquals(Duration.ZERO, dur.toDuration() )
    }

    @Test
    fun invalidDayNonDigitToDuration() {
        val subj = arrayOf(
            "86:x:41:31", "78:x:5:51", "88:x:5:59", "86:x:9:39", "46:x:43:45",
            "96:x:41:48", "18:x:49:25", "6:x:2:17", "40:x:41:49", "27:x:18:21"
        )
        for( dur in subj )
            assertEquals(Duration.ZERO, dur.toDuration() )
    }

    @Test
    fun invalidHourRangeToDuration() {
       val subj = arrayOf(
           "48:0:52", "100:31:12", "80:35:14", "77:42:6", "85:56:16", "76:18:37", "97:4:28", "46:3:41", "96:18:31", "84:0:23"
       )
        for( dur in subj )
            assertEquals(Duration.ZERO, dur.toDuration() )
    }

    @Test
    fun validMinuteToDuration() {
        val subj = mapOf(
            "06:28" to 388, "19:19" to 1159, "20:35" to 1235, "18:24" to 1104, "07:58" to 478,
            "15:10" to 910, "0:27" to 27, "6:09" to 369, "11:08" to 668, "1:04" to 64,
            "2:8" to 128, "0:4" to 4, "1:9" to 69, "7:7" to 427, "5:9" to 309,
            "13:3" to 783, "11:1" to 661, "20:1" to 1201, "23:4" to 1384, "08:2" to 482
        )
        subj.forEach { dur, secs ->
            val expected = secs.toDuration(DurationUnit.SECONDS )
            assertEquals( expected, dur.toDuration() )
        }
    }

    @Test
    fun validHourToDuration() {
        val subj = mapOf(
            "21:31:32" to 77492, "15:17:23" to 55043, "19:14:52" to 69292, "18:48:05" to 67685, "13:15:13" to 47713,
            "2:52:51" to 10371, "1:43:18" to 6198, "7:31:39" to 27099, "2:34:45" to 9285, "0:15:21" to 921,
            "2:11:59" to 7919, "2:22:28" to 8548, "9:34:42" to 34482, "3:50:57" to 13857, "7:32:1" to 27121
        )
        subj.forEach { dur, secs ->
            val expected = secs.toDuration(DurationUnit.SECONDS )
            assertEquals( expected, dur.toDuration() )
        }
    }

    @Test
    fun validDayToDuration() {
        val subj = mapOf(
            "128:20:06:28" to 11131588, "108:8:19:19" to 9361159, "183:4:20:35" to 15826835,
            "108:8:18:24" to 9361104, "95:12:07:58" to 8251678, "117:12:15:10" to 10152910,
            "4:18:0:27" to 410427, "34:6:6:09" to 2959569, "127:4:11:08" to 10987868,
            "88:2:1:04" to 7610464, "23:18:2:8" to 2052128, "123:1:0:4" to 10630804,
            "123:10:1:9" to 10663269, "139:16:7:7" to 12067627, "92:3:5:9" to 7959909,
            "92:14:13:3" to 7999983, "159:5:11:1" to 13756261, "161:21:20:1" to 13987201,
            "41:15:23:4" to 3597784, "135:0:08:2" to 11664482, "15:03:42:59" to 1309379
        )
        subj.forEach { dur, secs ->
            val expected = secs.toDuration(DurationUnit.SECONDS )
            assertEquals( expected, dur.toDuration() )
        }
    }

    @Test
    fun zeroDurationReadableText() {
        assertEquals( "00:00", Duration.ZERO.readableText() )
    }

    @Test
    fun validDurationReadableText() {
        val subj = mapOf(
            "128:20:06:28" to 11131588, "108:08:19:19" to 9361159, "183:04:20:35" to 15826835,
            "108:08:18:24" to 9361104, "95:12:07:58" to 8251678, "117:12:15:10" to 10152910,
            "4:18:00:27" to 410427, "34:06:06:09" to 2959569, "127:04:11:08" to 10987868,
            "88:02:01:04" to 7610464, "23:18:02:08" to 2052128, "123:01:00:04" to 10630804,
            "123:10:01:09" to 10663269, "139:16:07:07" to 12067627, "92:03:05:09" to 7959909,
            "92:14:13:03" to 7999983, "159:05:11:01" to 13756261, "161:21:20:01" to 13987201,
            "41:15:23:04" to 3597784, "135:00:08:02" to 11664482, "15:03:42:59" to 1309379
        )
        subj.forEach { dur, secs ->
            val actual = secs.toDuration(DurationUnit.SECONDS )
            assertEquals( dur, actual.readableText() )
        }
    }
}