package com.example.dbversionmanager;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {

    private static final String TAG = "DatabaseHelper";
    private static DatabaseHelper instance;
    private static String DATABASE_NAME = "mydb.db";
    private static int DATABASE_VERSION = 20;

    private Map<String, Dao> daoMap;
    private ArrayList<Class<?>> clazzList;

    static DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (DatabaseHelper.class) {
                if (instance == null) {
                    instance = new DatabaseHelper(context);
                }
            }
        }

        return instance;
    }

    public synchronized Dao getDao(Class clazz) throws SQLException {
        Dao dao = null;
        String className = clazz.getSimpleName();
        if (daoMap.containsKey(className)) {
            dao = daoMap.get(className);
        }
        if (dao == null) {
            dao = super.getDao(clazz);
            daoMap.put(className, dao);
        }

        return dao;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        daoMap = new HashMap<>();
        clazzList = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            clazzList.addAll(DatabaseUtil.getClasses(context, "com.example.dbversionmanager.bean"));
        }
        Log.d(TAG, "DatabaseHelper");
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase, ConnectionSource connectionSource) {

        Log.d(TAG, "onCreate");
        if (clazzList != null) {
            for (Class<?> clazz : clazzList) {
                try {
                    if (!DatabaseUtil.tableIsExist(sqLiteDatabase, DatabaseUtil.extractTableName(clazz))) {
                        TableUtils.createTable(connectionSource, clazz);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        for (Class clazz : clazzList) {
            if (DatabaseUtil.tableIsExist(sqLiteDatabase, DatabaseUtil.extractTableName(clazz))) {
                DatabaseUtil.updateTable(sqLiteDatabase, connectionSource, clazz);
            } else {
                onCreate(sqLiteDatabase, connectionSource);
            }
        }
    }

    @Override
    public void close() {
        super.close();

        for (String key : daoMap.keySet()) {
            daoMap.put(key, null);
        }
        daoMap.clear();
    }
}
