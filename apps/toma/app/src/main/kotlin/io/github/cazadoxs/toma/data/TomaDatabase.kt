package io.github.cazadoxs.toma.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ProfileEntity::class, MedicationEntity::class, DoseEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TomaDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun medications(): MedicationDao
    abstract fun doses(): DoseDao

    companion object {
        fun create(context: Context): TomaDatabase =
            Room.databaseBuilder(context, TomaDatabase::class.java, "toma.db").build()
    }
}
