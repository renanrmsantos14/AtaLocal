package br.com.betinhos.atalocal.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.betinhos.atalocal.domain.MeetingStatus

class MeetingConverters {
    @TypeConverter fun fromStatus(value: MeetingStatus): String = value.name
    @TypeConverter fun toStatus(value: String): MeetingStatus = MeetingStatus.valueOf(value)
}

@Database(
    entities = [MeetingEntity::class, ProcessingJobEntity::class, TranscriptSegmentEntity::class, ModelInstallEntity::class, ArtifactEntity::class, AudioSegmentEntity::class],
    version = 4,
    exportSchema = true
)
@TypeConverters(MeetingConverters::class)
abstract class AtaLocalDatabase : RoomDatabase() {
    abstract fun meetingDao(): MeetingDao
    abstract fun processingJobDao(): ProcessingJobDao
    abstract fun transcriptSegmentDao(): TranscriptSegmentDao
    abstract fun modelInstallDao(): ModelInstallDao
    abstract fun artifactDao(): ArtifactDao
    abstract fun audioSegmentDao(): AudioSegmentDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE transcript_segments ADD COLUMN editedText TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS audio_segments (`meetingId` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `path` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `status` TEXT NOT NULL, `sha256` TEXT, `error` TEXT, PRIMARY KEY(`meetingId`, `sequence`))")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE model_installs ADD COLUMN status TEXT NOT NULL DEFAULT 'INSTALLED'")
        database.execSQL("ALTER TABLE model_installs ADD COLUMN downloadedBytes INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE model_installs ADD COLUMN error TEXT")
    }
}
