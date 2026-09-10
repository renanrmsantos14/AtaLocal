package br.com.betinhos.atalocal.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import br.com.betinhos.atalocal.domain.MeetingStatus

class MeetingConverters {
    @TypeConverter fun fromStatus(value: MeetingStatus): String = value.name
    @TypeConverter fun toStatus(value: String): MeetingStatus = MeetingStatus.valueOf(value)
}

@Database(
    entities = [MeetingEntity::class, ProcessingJobEntity::class, TranscriptSegmentEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(MeetingConverters::class)
abstract class AtaLocalDatabase : RoomDatabase() {
    abstract fun meetingDao(): MeetingDao
    abstract fun processingJobDao(): ProcessingJobDao
    abstract fun transcriptSegmentDao(): TranscriptSegmentDao
}
