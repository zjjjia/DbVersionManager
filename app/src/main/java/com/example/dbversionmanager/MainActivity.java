package com.example.dbversionmanager;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.example.dbversionmanager.bean.TestABean;
import com.example.dbversionmanager.bean.TestBBean;
import com.facebook.stetho.Stetho;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Stetho.initializeWithDefaults(this);

        TestABean testABean = new TestABean();
        testABean.setName("test2");
        testABean.setAge(19);
        testABean.setGender("man");
//        testABean.setCountry("中国");
        testABean.setCity("深圳");
        TestADao.getInstance(this).insert(testABean);

        TestBBean testBBean = new TestBBean();
        testBBean.setClassName("英语");
        testBBean.setGrade(99);
        TestBDao.getInstance(this).insert(testBBean);


    }
}
