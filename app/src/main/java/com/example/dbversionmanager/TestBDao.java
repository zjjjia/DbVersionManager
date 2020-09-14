package com.example.dbversionmanager;

import android.content.Context;

import com.example.dbversionmanager.bean.TestBBean;
import com.j256.ormlite.dao.Dao;

import java.sql.SQLException;

public class TestBDao {

    private static TestBDao instance;
    private Dao<TestBBean, Integer> mDao;

    public static TestBDao getInstance(Context context) {
        if (instance == null) {
            synchronized (TestBDao.class) {
                if (instance == null) {
                    instance = new TestBDao(context);
                }
            }
        }

        return instance;
    }

    private TestBDao(Context context) {
        try {
            mDao = DatabaseHelper.getInstance(context).getDao(TestBBean.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void insert(TestBBean bean) {
        try {
            mDao.create(bean);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
