package com.example.dbversionmanager;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.DatabaseTable;
import com.j256.ormlite.table.TableUtils;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Objects;

import dalvik.system.DexFile;

public class DatabaseUtil {

    private static final String TAG = "DatabaseUtil";

    public static boolean tableIsExist(SQLiteDatabase db, String tableName) {
        boolean result = false;
        if (tableName == null) {
            return false;
        }
        Cursor cursor = null;
        try {

            String sql = "select count(*) as c from Sqlite_master where type = 'table' and name ='" + tableName.trim() + "'";
            cursor = db.rawQuery(sql, null);
            if (cursor.moveToNext()) {
                int count = cursor.getInt(0);
                if (count > 0) {
                    result = true;
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    /**
     * 获取包下面带注解DatabaseTable的bean类的Class对象的列表
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static ArrayList<Class<?>> getClasses(Context mContext, String packageName) {
        ArrayList<Class<?>> classes = new ArrayList<>();
        try {
            String packageCodePath = mContext.getPackageCodePath();
            DexFile df = new DexFile(packageCodePath);
            String regExp = "^" + packageName + ".\\w+$";
            for (Enumeration iter = df.entries(); iter.hasMoreElements(); ) {
                String className = (String) iter.nextElement();
                if (className.matches(regExp)) {
                    Class clazz = Class.forName(className);
                    if (clazz.getDeclaredAnnotation(DatabaseTable.class) != null) {
                        classes.add(clazz);
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return classes;
    }

    public static <T> void updateTable(SQLiteDatabase db, ConnectionSource connectionSource, Class<T> clazz) {
        String tableName = extractTableName(clazz);

        db.beginTransaction();
        //重命名
        try {
            String tempTableName = tableName + "_temp";
            String sql = "ALTER TABLE " + tableName + " RENAME TO " + tempTableName;
            db.execSQL(sql);
            Log.d(TAG, "updateTable: " + sql);

            sql = TableUtils.getCreateTableStatements(connectionSource, clazz).get(0);
            db.execSQL(sql);

            String columns = getCopyColumnsStr(db, tableName, tempTableName);

            //columns 为空说明该表没有发生变化，将临时表名字改回来
            if (columns == null) {
                sql = "DROP TABLE IF EXISTS " + tableName;
                Log.d(TAG, "updateTable: " + sql);
                db.execSQL(sql);
                sql = "ALTER TABLE " + tempTableName + " RENAME TO " + tableName;
                Log.d(TAG, "updateTable: " + sql);
                db.execSQL(sql);
            } else {
                sql = "INSERT INTO " + tableName + " (" + columns + ") SELECT " + columns + " FROM " + tempTableName;
                Log.d(TAG, "updateTable: " + sql);
                db.execSQL(sql);
                //删除临时表
                sql = "DROP TABLE IF EXISTS " + tempTableName;
                Log.d(TAG, "updateTable: " + sql);
                db.execSQL(sql);

            }
            db.setTransactionSuccessful();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    private static String getCopyColumnsStr(SQLiteDatabase db, String newTableName, String tempTableName) {
        ArrayList<String> tempColumnsList = Objects.requireNonNull(getColumnsNames(db, tempTableName));
        ArrayList<String> newTableColumnsList = Objects.requireNonNull(getColumnsNames(db, newTableName));

        boolean isChanged = false;
        for (int i = 0; i < tempColumnsList.size(); i++) {
            if (!newTableColumnsList.contains(tempColumnsList.get(i))) {
                tempColumnsList.remove(tempColumnsList.get(i));
                isChanged = true;
            }
        }

        //表的列没有发生变化时返回null
        isChanged = isChanged || tempColumnsList.size() < newTableColumnsList.size();
        String columnsStr = Arrays.toString(tempColumnsList.toArray()).replace("[", "").replace("]", "");
        return isChanged ? columnsStr : null;
    }

    private static ArrayList<String> getColumnsNames(SQLiteDatabase db, String tableName) {
        Cursor cursor = null;
        ArrayList<String> columns = new ArrayList<>();
        try {
            cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
            if (cursor != null) {
                int columnsIndex = cursor.getColumnIndex("name");
                if (columnsIndex == -1) {
                    return null;
                }
                int index = 0;
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    columns.add(cursor.getString(columnsIndex));
                    index++;
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return columns;
    }

    /**
     * 获取表名
     */
    public static <T> String extractTableName(Class<T> clazz) {
        DatabaseTable databaseTable = clazz.getAnnotation(DatabaseTable.class);
        String name = null;
        if (databaseTable != null) {
            name = databaseTable.tableName();
        }
        return name;
    }


}