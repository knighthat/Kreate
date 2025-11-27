package me.knighthat.kreate.database.tables

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update
import androidx.room.Upsert


/**
 * A set of commonly used DAO suspend functions. This is introduced in order
 * to reduce the times needed to implement individual suspend function
 * to a DAO class.
 */
interface DaoTable<T> {

    /**
     * Attempt to write a [record] into database.
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
    suspend fun insertIgnore( record: T )

    /**
     * Attempt to write the list of [records] to database.
     *
     * If record exist (determined by its primary key),
     * it'll simply be ignored and the transaction continues.
     *
     * @param records list of [T] to insert to database
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore( records: List<T> )

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
    suspend fun updateReplace( record: T ): Int

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
    suspend fun updateReplace( records: List<T> ): Int

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
    suspend fun updateIgnore( record: T )

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
    suspend fun upsert( record: T )

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
    suspend fun upsert( records: List<T> )

    /**
     * @param record intended to delete from database
     */
    @Delete
    suspend fun delete( record: T )

    /**
     * Attempt to remove records from database.
     *
     * @param records list of [T] to delete from database
     *
     * @return number of rows affected by the this operation
     */
    @Delete
    suspend fun delete( records: List<T> ): Int
}