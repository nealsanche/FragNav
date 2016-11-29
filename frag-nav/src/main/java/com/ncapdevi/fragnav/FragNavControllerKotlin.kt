package com.ncapdevi.fragnav

import android.os.Bundle
import android.support.annotation.IdRes
import android.support.annotation.IntDef
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction

import org.json.JSONArray

import kotlin.annotation.AnnotationRetention
import java.util.ArrayList
import java.util.Stack

/**
 * The class is used to manage navigation through multiple stacks of fragments, as well as coordinate
 * fragments that may appear on screen

 * https://github.com/ncapdevi/FragNav
 * Nic Capdevila
 * Nic.Capdevila@gmail.com

 * Originally Created March 2016
 */
class FragNavControllerKotlin  private constructor (private val mFragmentManager: FragmentManager, @IdRes private val mContainerId: Int, numberOfTabs: Int) {


    //region Construction and setup

    @TabIndex
    private var mSelectedTabIndex = -1L
    private var mTagCount: Int = 0
    private var mCurrentFrag: Fragment? = null
    private var mCurrentDialogFrag: DialogFragment? = null


    private var mTransactionListener: TransactionListener? = null

    @Transit
    private var mTransitionMode = FragmentTransaction.TRANSIT_UNSET

    private var mExecutingTransaction: Boolean = false

    private val mFragmentStacks: MutableList<Stack<Fragment>>

    init {
        mFragmentStacks = ArrayList<Stack<Fragment>>(numberOfTabs)
    }

    private var mRootFragmentListener: RootFragmentListener = null




    /**
     * @param savedInstanceState savedInstanceState to allow for recreation of FragNavController and its fragments if possible
     * *
     * @param fragmentManager    FragmentManager to be used
     * *
     * @param containerId        The resource ID of the layout in which the fragments will be placed
     * *
     * @param rootFragment       A single root fragment. This library can still be helpful when mangiging a single stack of fragments.
     */


    constructor(savedInstanceState: Bundle?,  fragmentManager: FragmentManager, @IdRes containerId: Int, rootFragment: Fragment) : this(fragmentManager, containerId, 1) {

        //Attempt to restore from bundle, if not, initialize
        val rootFragments = ArrayList<Fragment>(1)
        rootFragments.add(rootFragment)

        if (!restoreFromBundle(savedInstanceState, rootFragments)) {
            val stack = Stack<Fragment>()
            stack.add(rootFragment)
            mFragmentStacks.add(stack)
            initialize(TAB1)
        }
    }

    /**
     * @param savedInstanceState savedInstanceState to allow for recreation of FragNavController and its fragments if possible
     * *
     * @param fragmentManager    FragmentManager to be used
     * *
     * @param containerId        The resource ID of the layout in which the fragments will be placed
     * *
     * @param rootFragments      a list of root fragments. root Fragments are the root fragments that exist on any tab structure. If only one fragment is sent in,
     * *                           fragnav will still manage transactions
     * *
     * @param startingIndex      The initial tab index to be used must be in range of rootFragments size
     */
    constructor(savedInstanceState: Bundle, fragmentManager: FragmentManager, @IdRes containerId: Int, rootFragments: List<Fragment>, @TabIndex startingIndex: Long) : this(fragmentManager, containerId, rootFragments.size) {
        if (startingIndex > rootFragments.size) {
            throw IndexOutOfBoundsException("Starting index cannot be larger than the number of stacks")
        }
        //Attempt to restore from bundle, if not, initialize
        if (!restoreFromBundle(savedInstanceState, rootFragments)) {
            for (fragment in rootFragments) {
                val stack = Stack<Fragment>()
                stack.add(fragment)
                mFragmentStacks!!.add(stack)
            }
            initialize(startingIndex)
        }
    }

    /**
     * @param savedInstanceState savedInstanceState to allow for recreation of FragNavController and its fragments if possible
     * *
     * @param fragmentManager    FragmentManager to be used
     * *
     * @param containerId        The resource ID of the layout in which the fragments will be placed
     * *
     * @param rootFragmentListener        A listener to be implemented (typically within the main activity) to perform certain interactions.
     * *
     * @param numberOfTabs       The number of different fragment stacks to be managed (maximum of five)
     * *
     * @param startingIndex      The initial tab index to be used must be in range of rootFragments size
     */
    constructor(savedInstanceState: Bundle, fragmentManager: FragmentManager, @IdRes containerId: Int, rootFragmentListener: RootFragmentListener, numberOfTabs: Int, @TabIndex startingIndex: Long) : this(fragmentManager, containerId, numberOfTabs) {

        if (startingIndex > numberOfTabs) {
            throw IndexOutOfBoundsException("Starting index cannot be larger than the number of stacks")
        }

        setRootFragmentListener(rootFragmentListener)

        //Attempt to restore from bundle, if not, initialize
        if (!restoreFromBundle(savedInstanceState, null)) {
            for (i in 0..numberOfTabs - 1) {
                mFragmentStacks!!.add(Stack<Fragment>())
            }
            initialize(startingIndex)
        }
    }


    /**

     * @param rootFragmentListener a listener that allows for dynamically creating root fragments
     */
    fun setRootFragmentListener(rootFragmentListener: RootFragmentListener) {
        mRootFragmentListener = rootFragmentListener
    }

    /**

     * @param transactionListener        A listener to be implemented (typically within the main activity) to fragment transactions (including tab switches);
     */
    fun setTransactionListener(transactionListener: TransactionListener) {
        mTransactionListener = transactionListener
    }

    /**

     * @param transitionMode The type of transition to be used during fragment transactions
     */
    fun setTransitionMode(@Transit transitionMode: Int) {
        mTransitionMode = transitionMode
    }
    //endregion

    //region Transactions

    /**
     * Switch to a different tab. Should not be called on the current tab.

     * @param index the index of the tab to switch to
     * *
     * @throws IndexOutOfBoundsException If the index to switch to is out of range
     */

    @Throws(IndexOutOfBoundsException::class)
    fun switchTab(@TabIndex index: Long) {
        //Check to make sure the tab is within range
        if (index >= mFragmentStacks!!.size) {
            throw IndexOutOfBoundsException("Can't switch to a tab that hasn't been initialized, " +
                    "Index : " + index + ", current stack size : " + mFragmentStacks.size +
                    ". Make sure to create all of the tabs you need in the Constructor or provide a way for them to be created via RootFragmentListener.")
        }
        if (mSelectedTabIndex != index) {
            mSelectedTabIndex = index

            val ft = mFragmentManager.beginTransaction()
            ft.setTransition(mTransitionMode)

            detachCurrentFragment(ft)

            //Attempt to reattach previous fragment
            var fragment = reattachPreviousFragment(ft)
            if (fragment != null) {
                ft.commit()
            } else {
                fragment = getRootFragment(mSelectedTabIndex)
                ft.add(mContainerId, fragment, generateTag(fragment))
                ft.commit()
            }

            executePendingTransactions()

            mCurrentFrag = fragment
            if (mTransactionListener != null) {
                mTransactionListener!!.onTabTransaction(mCurrentFrag, mSelectedTabIndex)
            }
        }
    }

    /**
     * Push a fragment onto the current stack

     * @param fragment The fragment that is to be pushed
     */
    fun push(fragment: Fragment?) {
        if (fragment != null) {

            val ft = mFragmentManager.beginTransaction()
            ft.setTransition(mTransitionMode)
            detachCurrentFragment(ft)
            ft.add(mContainerId, fragment, generateTag(fragment))
            ft.commit()

            executePendingTransactions()

            mFragmentStacks!![mSelectedTabIndex].push(fragment)

            mCurrentFrag = fragment
            if (mTransactionListener != null) {
                mTransactionListener!!.onFragmentTransaction(mCurrentFrag)
            }

        }
    }

    /**
     * Pop the current fragment from the current tab
     */
    fun pop() {
        val poppingFrag = currentFrag
        if (poppingFrag != null) {
            val ft = mFragmentManager.beginTransaction()
            ft.setTransition(mTransitionMode)
            ft.remove(poppingFrag)

            //overly cautious fragment pop
            val fragmentStack = mFragmentStacks!![mSelectedTabIndex]
            if (!fragmentStack.isEmpty()) {
                fragmentStack.pop()
            }

            //Attempt reattach, if we can't, try to pop from the stack and push that on
            var fragment = reattachPreviousFragment(ft)
            if (fragment == null && !fragmentStack.isEmpty()) {
                fragment = fragmentStack.peek()
                ft.add(mContainerId, fragment, fragment!!.tag)
            }

            //Commit our transactions
            ft.commit()

            executePendingTransactions()

            mCurrentFrag = fragment
            if (mTransactionListener != null) {
                mTransactionListener!!.onFragmentTransaction(mCurrentFrag)
            }
        }
    }

    /**
     * Clears the current tab's stack to get to just the bottom Fragment. This will reveal the root fragment,
     */
    fun clearStack() {
        //Grab Current stack
        val fragmentStack = mFragmentStacks!![mSelectedTabIndex]

        // Only need to start popping and reattach if the stack is greater than 1
        if (fragmentStack.size > 1) {
            var fragment: Fragment?
            val ft = mFragmentManager.beginTransaction()
            ft.setTransition(mTransitionMode)

            //Pop all of the fragments on the stack and remove them from the FragmentManager
            while (fragmentStack.size > 1) {
                fragment = mFragmentManager.findFragmentByTag(fragmentStack.peek().tag)
                if (fragment != null) {
                    fragmentStack.pop()
                    ft.remove(fragment)
                }
            }

            //Attempt to reattach previous fragment
            fragment = reattachPreviousFragment(ft)

            var bShouldPush = false
            //If we can't reattach, either pull from the stack, or create a new root fragment
            if (fragment != null) {
                ft.commit()
            } else {
                if (!fragmentStack.isEmpty()) {
                    fragment = fragmentStack.peek()
                    ft.add(mContainerId, fragment, fragment!!.tag)
                    ft.commit()
                } else {
                    fragment = getRootFragment(mSelectedTabIndex)
                    ft.add(mContainerId, fragment, generateTag(fragment))
                    ft.commit()

                    bShouldPush = true
                }
            }

            executePendingTransactions()

            if (bShouldPush) {
                mFragmentStacks[mSelectedTabIndex].push(fragment)
            }

            //Update the stored version we have in the list
            mFragmentStacks[mSelectedTabIndex] = fragmentStack

            mCurrentFrag = fragment
            if (mTransactionListener != null) {
                mTransactionListener!!.onFragmentTransaction(mCurrentFrag)
            }
        }
    }

    /**
     * Replace the current fragment

     * @param fragment
     */
    fun replace(fragment: Fragment) {
        val poppingFrag = currentFrag

        if (poppingFrag != null) {
            val ft = mFragmentManager.beginTransaction()
            ft.setTransition(mTransitionMode)

            //overly cautious fragment pop
            val fragmentStack = mFragmentStacks!![mSelectedTabIndex]
            if (!fragmentStack.isEmpty()) {
                fragmentStack.pop()
            }

            val tag = generateTag(fragment)
            ft.replace(mContainerId, fragment, tag)

            //Commit our transactions
            ft.commit()

            executePendingTransactions()

            fragmentStack.push(fragment)
            mCurrentFrag = fragment

            if (mTransactionListener != null) {
                mTransactionListener!!.onFragmentTransaction(mCurrentFrag)

            }
        }
    }
    //endregion

    //region Private helper functions

    /**
     * Helper function to make sure that we are starting with a clean slate and to perform our first fragment interaction.
     * @param index the tab index to initialize to
     */
    private fun initialize(@TabIndex index: Long) {
        mSelectedTabIndex = index
        clearFragmentManager()
        clearDialogFragment()

        val ft = mFragmentManager.beginTransaction()
        ft.setTransition(mTransitionMode)

        val fragment = getRootFragment(index)
        ft.add(mContainerId, fragment, generateTag(fragment))
        ft.commit()

        executePendingTransactions()

        mCurrentFrag = fragment
        if (mTransactionListener != null) {
            mTransactionListener!!.onTabTransaction(mCurrentFrag, mSelectedTabIndex)
        }
    }

    /**
     * Helper function to get the root fragment for a given index. This is done by either passing them in the constructor, or dynamically via NavListner
     * @param index The tab index to get this fragment from
     * *
     * @return The root fragment at this index
     * *
     * @throws IllegalStateException This will be thrown if we can't find a rootFragment for this index. Either because you didn't provide it in the
     * *                              constructor, or because your RootFragmentListener.getRootFragment(index) isn't returning a fragment for this index.
     */
    @Throws(IllegalStateException::class)
    private fun getRootFragment(index: Long): Fragment {
        var fragment: Fragment? = null
        if (!mFragmentStacks!![index].isEmpty()) {
            fragment = mFragmentStacks[index].peek()
        } else if (mRootFragmentListener != null) {
            fragment = mRootFragmentListener!!.getRootFragment(index)
            mFragmentStacks[mSelectedTabIndex].push(fragment)

        }
        if (fragment == null) {
            throw IllegalStateException("Either you haven't past in a fragment at this index in your constructor, or you haven't" + "provided a way to create it while via your RootFragmentListener.getRootFragment(index)")
        }

        return fragment
    }

    /**
     * Will attempt to reattach a previous fragment in the FragmentManager, or return null if not able to,

     * @param ft current fragment transaction
     * *
     * @return Fragment if we were able to find and reattach it
     */
    private fun reattachPreviousFragment(ft: FragmentTransaction): Fragment? {
        val fragmentStack = mFragmentStacks!![mSelectedTabIndex]
        var fragment: Fragment? = null
        if (!fragmentStack.isEmpty()) {
            fragment = mFragmentManager.findFragmentByTag(fragmentStack.peek().tag)
            if (fragment != null) {
                ft.attach(fragment)
            }
        }
        return fragment
    }

    /**
     * Attemps to detach any current fragment if it exists, and if none is found, returns;

     * @param ft the current transaction being performed
     */
    private fun detachCurrentFragment(ft: FragmentTransaction) {
        val oldFrag = currentFrag
        if (oldFrag != null) {
            ft.detach(oldFrag)
        }
    }

    /**
     * Helper function to attempt to get current fragment

     * @return
     */
    //Attempt to used stored current fragment
    //if not, try to pull it from the stack
    val currentFrag: Fragment?
        get() {
            if (mCurrentFrag != null) {
                return mCurrentFrag
            } else {
                val fragmentStack = mFragmentStacks!![mSelectedTabIndex]
                if (!fragmentStack.isEmpty()) {
                    mCurrentFrag = mFragmentManager.findFragmentByTag(mFragmentStacks[mSelectedTabIndex].peek().tag)
                }
            }
            return mCurrentFrag
        }

    /**
     * Create a unique fragment tag so that we can grab the fragment later from the FragmentManger

     * @param fragment The fragment that we're creating a unique tag for
     * *
     * @return a unique tag using the fragment's class name
     */
    private fun generateTag(fragment: Fragment): String {
        return fragment.javaClass.name + ++mTagCount
    }

    /**
     * This check is here to prevent recursive entries into executePendingTransactions
     */
    private fun executePendingTransactions() {
        if (!mExecutingTransaction) {
            mExecutingTransaction = true
            mFragmentManager.executePendingTransactions()
            mExecutingTransaction = false
        }
    }

    /**
     * Private helper function to clear out the fragment manager on initialization. All fragment management should be done via FragNav
     */
    private fun clearFragmentManager() {
        if (mFragmentManager.fragments != null) {
            val ft = mFragmentManager.beginTransaction()
            ft.setTransition(mTransitionMode)
            for (fragment in mFragmentManager.fragments) {
                ft.remove(fragment)
            }
            ft.commit()
            executePendingTransactions()
        }
    }
    //endregion

    //region Public helper functions

    /**
     * Get the number of fragment stacks
     * @return the number of fragment stacks
     */
    val size: Int
        get() {
            if (mFragmentStacks == null) {
                return 0
            }
            return mFragmentStacks.size
        }

    /**
     * Get the current stack that is being displayed
     * @return Current stack
     */
    val currentStack: Stack<Fragment>
        get() = mFragmentStacks!![mSelectedTabIndex]

    /**

     * @return If you are able to pop the current stack. If false, you are at the bottom of the stack
     * * (Consider using replace if you need to change the root fragment for some reason)
     */
    fun canPop(): Boolean {
        return currentStack.size > 1
    }

    /**

     * @return Current DialogFragment being displayed. Null if none
     */
    //Else try to find one in the fragmentmanager
    val currentDialogFrag: DialogFragment?
        get() {
            if (mCurrentDialogFrag != null) {
                return mCurrentDialogFrag
            } else {
                val fragmentManager: FragmentManager
                if (mCurrentFrag != null) {
                    fragmentManager = mCurrentFrag!!.childFragmentManager
                } else {
                    fragmentManager = mFragmentManager
                }
                if (fragmentManager.fragments != null) {
                    for (fragment in fragmentManager.fragments) {
                        if (fragment is DialogFragment) {
                            mCurrentDialogFrag = fragment
                            break
                        }
                    }
                }
            }
            return mCurrentDialogFrag
        }

    /**
     * Clear any DialogFragments that may be shown
     */
    fun clearDialogFragment() {
        if (mCurrentDialogFrag != null) {
            mCurrentDialogFrag!!.dismiss()
            mCurrentDialogFrag = null
        } else {
            val fragmentManager: FragmentManager
            if (mCurrentFrag != null) {
                fragmentManager = mCurrentFrag!!.childFragmentManager
            } else {
                fragmentManager = mFragmentManager
            }

            if (fragmentManager.fragments != null) {
                for (fragment in fragmentManager.fragments) {
                    if (fragment is DialogFragment) {
                        fragment.dismiss()
                    }
                }
            }
        }// If we don't have the current dialog, try to find and dismiss it
    }

    /**
     * Display a DialogFragment on the screen
     * @param dialogFragment The Fragment to be Displayed
     */
    fun showDialogFragment(dialogFragment: DialogFragment?) {
        if (dialogFragment != null) {
            val fragmentManager: FragmentManager
            if (mCurrentFrag != null) {
                fragmentManager = mCurrentFrag!!.childFragmentManager
            } else {
                fragmentManager = mFragmentManager
            }

            //Clear any current dialogfragments
            if (fragmentManager.fragments != null) {
                for (fragment in fragmentManager.fragments) {
                    if (fragment is DialogFragment) {
                        fragment.dismiss()
                        mCurrentDialogFrag = null
                    }
                }
            }

            mCurrentDialogFrag = dialogFragment
            try {
                dialogFragment.show(fragmentManager, dialogFragment.javaClass.name)
            } catch (e: IllegalStateException) {
                // Activity was likely destroyed before we had a chance to show, nothing can be done here.
            }

        }
    }

    //endregion

    //region SavedInstanceState

    /**
     * Call this in your Activity's onSaveInstanceState(Bundle outState) method to save the instance's state.

     * @param outState The Bundle to save state information to
     */
    fun onSaveInstanceState(outState: Bundle) {

        // Write tag count
        outState.putInt(EXTRA_TAG_COUNT, mTagCount)

        // Write select tab
        outState.putInt(EXTRA_SELECTED_TAB_INDEX, mSelectedTabIndex)

        // Write current fragment
        if (mCurrentFrag != null) {
            outState.putString(EXTRA_CURRENT_FRAGMENT, mCurrentFrag!!.tag)
        }

        // Write stacks
        try {
            val stackArrays = JSONArray()

            for (stack in mFragmentStacks!!) {
                val stackArray = JSONArray()

                for (fragment in stack) {
                    stackArray.put(fragment.tag)
                }

                stackArrays.put(stackArray)
            }

            outState.putString(EXTRA_FRAGMENT_STACK, stackArrays.toString())
        } catch (t: Throwable) {
            // Nothing we can do
        }

    }

    /**
     * Restores this instance to the state specified by the contents of savedInstanceState

     * @param savedInstanceState The bundle to restore from
     * *
     * @param rootFragments      List of root fragments from which to initialize empty stacks. If null, pull fragments from RootFragmentListener
     * *
     * @return true if successful, false if not
     */
    private fun restoreFromBundle(savedInstanceState: Bundle?, rootFragments: List<Fragment>?): Boolean {
        if (savedInstanceState == null) {
            return false
        }

        // Restore tag count
        mTagCount = savedInstanceState.getInt(EXTRA_TAG_COUNT, 0)

        // Restore current fragment
        mCurrentFrag = mFragmentManager.findFragmentByTag(savedInstanceState.getString(EXTRA_CURRENT_FRAGMENT))

        // Restore fragment stacks
        try {
            val stackArrays = JSONArray(savedInstanceState.getString(EXTRA_FRAGMENT_STACK))

            for (x in 0..stackArrays.length() - 1) {
                val stackArray = stackArrays.getJSONArray(x)
                val stack = Stack<Fragment>()

                if (stackArray.length() == 1) {
                    val tag = stackArray.getString(0)
                    val fragment: Fragment?

                    if (tag == null || "null".equals(tag, ignoreCase = true)) {
                        if (rootFragments != null) {
                            fragment = rootFragments[x]
                        } else {
                            fragment = getRootFragment(x)
                        }

                    } else {
                        fragment = mFragmentManager.findFragmentByTag(tag)
                    }

                    if (fragment != null) {
                        stack.add(fragment)
                    }
                } else {
                    for (y in 0..stackArray.length() - 1) {
                        val tag = stackArray.getString(y)

                        if (tag != null && !"null".equals(tag, ignoreCase = true)) {
                            val fragment = mFragmentManager.findFragmentByTag(tag)

                            if (fragment != null) {
                                stack.add(fragment)
                            }
                        }
                    }
                }

                mFragmentStacks!!.add(stack)
            }
            // Restore selected tab if we have one
            when (savedInstanceState.getInt(EXTRA_SELECTED_TAB_INDEX)) {
                TAB1 -> switchTab(TAB1)
                TAB2 -> switchTab(TAB2)
                TAB3 -> switchTab(TAB3)
                TAB4 -> switchTab(TAB4)
                TAB5 -> switchTab(TAB5)
            }

            //Succesfully restored state
            return true
        } catch (t: Throwable) {
            return false
        }

    }
    //endregion

    //Declare the TabIndex annotation
    @IntDef(TAB1, TAB2, TAB3, TAB4, TAB5)
    @Retention(AnnotationRetention.SOURCE)
    annotation class TabIndex


    // Declare Transit Styles
    @IntDef(FragmentTransaction.TRANSIT_NONE.toLong(), FragmentTransaction.TRANSIT_FRAGMENT_OPEN.toLong(), FragmentTransaction.TRANSIT_FRAGMENT_CLOSE.toLong(), FragmentTransaction.TRANSIT_FRAGMENT_FADE.toLong())
    @Retention(AnnotationRetention.SOURCE)
    private annotation class Transit

    interface RootFragmentListener {
        /**
         * Dynamically create the Fragment that will go on the bottom of the stack

         * @param index the index that the root of the stack Fragment needs to go
         * *
         * @return the new Fragment
         */
        fun getRootFragment(index: Int): Fragment
    }

    interface TransactionListener {

        fun onTabTransaction(fragment: Fragment, index: Int)

        fun onFragmentTransaction(fragment: Fragment)
    }

    companion object {
        //Declare the constants  There is a maximum of 5 tabs, this is per Material Design's Bottom Navigation's design spec.
        const val TAB1 = 0L
        const val TAB2 = 1L
        const val TAB3 = 2L
        const val TAB4 = 3L
        const val TAB5 = 4L

        // Extras used to store savedInstanceState
        private val EXTRA_TAG_COUNT = FragNavController::class.java.name + ":EXTRA_TAG_COUNT"
        private val EXTRA_SELECTED_TAB_INDEX = FragNavController::class.java.name + ":EXTRA_SELECTED_TAB_INDEX"
        private val EXTRA_CURRENT_FRAGMENT = FragNavController::class.java.name + ":EXTRA_CURRENT_FRAGMENT"
        private val EXTRA_FRAGMENT_STACK = FragNavController::class.java.name + ":EXTRA_FRAGMENT_STACK"
    }
}
