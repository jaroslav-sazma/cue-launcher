package app.cue.launcher.data.layout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderMemberDao {

    @Query("SELECT * FROM folder_member ORDER BY folderId, position")
    fun observeMembers(): Flow<List<FolderMemberEntity>>

    @Query("SELECT * FROM folder_member ORDER BY folderId, position")
    suspend fun members(): List<FolderMemberEntity>

    @Query("SELECT * FROM folder_member WHERE folderId = :folderId ORDER BY position")
    suspend fun membersOf(folderId: Long): List<FolderMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: FolderMemberEntity): Long

    @Query("DELETE FROM folder_member WHERE folderId = :folderId")
    suspend fun deleteMembers(folderId: Long): Int

    @Transaction
    suspend fun replaceMembers(folderId: Long, members: List<FolderMemberEntity>) {
        deleteMembers(folderId)
        members.forEach { member -> insertMember(member) }
    }

    @Query("DELETE FROM folder_member")
    suspend fun deleteAllMembers()

    @Query("SELECT DISTINCT userSerial FROM folder_member")
    suspend fun memberProfiles(): List<Long>

    @Query("SELECT DISTINCT packageName, userSerial FROM folder_member")
    suspend fun memberPackages(): List<StoredPackage>
}
