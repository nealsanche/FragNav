package com.ncapdevi.sample.activities

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.ncapdevi.fragnav.FragNavControllerKotlin
import com.ncapdevi.fragnav.R
import com.ncapdevi.sample.fragments.NearbyFragment
import kotlinx.android.synthetic.main.activity_bottom_tabs.*

class BottomTabsActivityKotlin : AppCompatActivity() {
var mFragNavController : FragNavControllerKotlin = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mFragNavController = FragNavControllerKotlin(savedInstanceState,supportFragmentManager, R.id.container,NearbyFragment.newInstance(2))
    }
}
