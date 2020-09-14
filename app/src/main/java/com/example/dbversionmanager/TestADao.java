package com.example.dbversionmanager;

import android.content.Context;

import com.example.dbversionmanager.bean.TestABean;
import com.j256.ormlite.dao.Dao;

import java.sql.SQLException;

public class TestADao {

    private Context mContext;
    private Dao<TestABean, Integer> dao;

    private static TestADao instance;

    public static TestADao getInstance(Context context) {
        if (instance == null) {
            synchronized (TestADao.class) {
                if (instance == null) {
                    instance = new TestADao(context);
                }
            }
        }

        return instance;
    }

    private TestADao(Context context) {
        mContext = context.getApplicationContext();
        try {
            dao = DatabaseHelper.getInstance(context).getDao(TestABean.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void insert(TestABean bean) {
        try {
            dao.create(bean);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


}
