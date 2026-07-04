package app.kreate.database.table

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.RawQuery
import androidx.room.RoomRawQuery
import androidx.room.Update
import androidx.room.Upsert
import org.jetbrains.annotations.Blocking


@Dao
interface DatabaseTable<T> {

    /**
     * Name of table
     */
    val tableName: String

    /**
     * Performs [statement] on current thread.
     *
     * This will block current thread in till it's finished.
     */
    @RawQuery
    @Blocking
    fun blockingGet( statement: RoomRawQuery ): List<T>

    /**
     * Return all records in the table.
     *
     * This will block current thread in till it's finished.
     */
    @Blocking
    fun blockingAll( limit: Int = Int.MAX_VALUE ): List<T> {
        val statement = RoomRawQuery("SELECT DISTINCT * FROM $tableName LIMIT $limit")
        return blockingGet( statement )
    }

    /**
     * Attempt to write [record] into database.
     *
     * ### Standalone use
     *
     * When error occurs, it'll simply be ignored.
     *
     * ### Transaction use
     *
     * When error occurs, it'll simply be ignored and the transaction continues.
     *
     * @param record intended to insert in to database
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( record: T )

    /**
     * Attempt to write the list of [T] to database.
     *
     * If record exist (determined by its primary key),
     * it'll simply be ignored and the transaction continues.
     *
     * @param records list of [T] to insert to database
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( records: List<T> )

    /**
     * Attempt to update a record's data with provided [record].
     *
     * ### Standalone use
     *
     * When error occurs, data inside database will be replaced by provided [record].
     *
     * ### Transaction use
     *
     * When error occurs, data inside database will be
     * replaced by provided [record] and transaction continues.
     *
     * @param record intended to update
     * @return number of rows affected by the this operation
     */
    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateReplace( record: T )

    /**
     * Attempt to replace each record's data with the one provided in records.
     *
     * ### Standalone use
     *
     * When an element fails to insert, it overrides existing
     * data with provided one.
     *
     * ### Transaction use
     *
     * When an element fails to insert, it overrides existing
     * data with provided one and transaction continues.
     *
     * @param records list of [T] to update
     *
     * @return number of rows affected by the this operation
     */
    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateReplace( records: List<T> ): Int

    /**
     * Attempt to update a record's data with provided [record].
     *
     * ### Standalone use
     *
     * When error occurs, it'll simply be ignored.
     *
     * ### Transaction use
     *
     * When error occurs, it'll simply be ignored and the transaction continues.
     *
     * @param record intended to update
     *
     * @return number of rows affected by the this operation
     */
    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun updateIgnore( record: T )

    /**
     * Attempt to write [record] into database.
     *
     * If [record] exist (determined by its primary key),
     * existing record's columns will be updated to
     * provided [record]'s data.
     *
     * @param record intended to insert in to database
     */
    @Upsert
    fun upsert( record: T )

    /**
     * Attempt to write the list of [T] to database.
     *
     * If record exist (determined by its primary key),
     * existing record's columns will be updated
     * by provided data.
     *
     * @param records list of [T] to insert to database
     */
    @Upsert
    fun upsert( records: List<T> )

    /**
     * @param record intended to delete from database
     */
    @Delete
    fun delete( record: T )

    /**
     * Attempt to remove records from database.
     *
     * @param records list of [T] to delete from database
     *
     * @return number of rows affected by the this operation
     */
    @Delete
    fun delete( records: List<T> ): Int
}