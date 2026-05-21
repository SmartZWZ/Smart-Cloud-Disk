package com.example.clouddisk;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface UserDao {
    @Insert
    void insert(User user);

    @Query("SELECT * FROM users WHERE username = :name AND password = :pass LIMIT 1")
    User login(String name, String pass);

    // 检查用户名是否已存在
    @Query("SELECT * FROM users WHERE username = :name LIMIT 1")
    User findByName(String name);

    @Query("DELETE FROM users WHERE username = :name")
    void deleteUser(String name);

    // --- 文件搜索索引相关 ---
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    void insertFileIndex(FileIndex index);

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    void insertAllFileIndices(java.util.List<FileIndex> indices);

    @Query("DELETE FROM file_index WHERE owner = :username")
    void clearFileIndex(String username);

    @Query("SELECT * FROM file_index WHERE owner = :username AND name LIKE '%' || :query || '%'")
    java.util.List<FileIndex> searchLocalFiles(String username, String query);

    @Query("DELETE FROM file_index WHERE owner = :username AND fullPath = :path")
    void deleteFileIndex(String username, String path);

    @Query("DELETE FROM file_index WHERE owner = :username AND fullPath LIKE :pathPrefix || '%'")
    void deleteFolderIndexRecursive(String username, String pathPrefix);

    @Query("SELECT SUM(size) FROM file_index WHERE owner = :username AND isDir = 0")
    long getTotalUsedSize(String username);

    @Query("SELECT * FROM file_index WHERE owner = :username")
    java.util.List<FileIndex> getAllFilesForAi(String username);
}