/*
 * This file is part of Batch Uninstaller.
 *
 * Batch Uninstaller is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Batch Uninstaller is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Batch Uninstaller.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */
package com.saha.batchuninstaller.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.content.*
import android.content.SharedPreferences.Editor
import android.content.pm.ApplicationInfo
import android.content.res.Resources.NotFoundException
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.text.format.Formatter
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.marcoscg.easylicensesdialog.EasyLicensesDialogCompat
import com.saha.batchuninstaller.AppInfo
import com.saha.batchuninstaller.debloat.UadDatabaseRepository
import com.saha.batchuninstaller.debloat.UadDatabaseScheduler
import com.saha.batchuninstaller.debloat.RestoreBackupResult
import com.saha.batchuninstaller.debloat.RestoreRepository
import com.saha.batchuninstaller.debloat.UninstallMode
import com.saha.batchuninstaller.debloat.UadPackageRecord
import com.saha.batchuninstaller.BuildConfig
import com.saha.batchuninstaller.R
import com.saha.batchuninstaller.RoomStorage.AppDatabase
import com.saha.batchuninstaller.RoomStorage.RestorePoint
import com.saha.batchuninstaller.databinding.ActivityMainBinding
import com.saha.batchuninstaller.ui.main.adapters.AppInfoAdapter
import com.saha.batchuninstaller.utils.PackageUtils
import com.saha.batchuninstaller.utils.PackageUtils.moveBatchUninstallerToEnd
import com.saha.batchuninstaller.utils.RootUtils.uninstallAppRoot
import com.stericson.RootTools.RootTools
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
	private var mAdapter: AppInfoAdapter? = null
	private var mApps: ArrayList<AppInfo> = arrayListOf()
    private var allApps: ArrayList<AppInfo> = arrayListOf()
    private var searchQuery: String = ""
    private var appFilter: Int = 2
    private lateinit var mAppsCopy : ArrayList<AppInfo>
	private var mPkgs: List<String> = arrayListOf()
	private var mFreeApps: ArrayList<ApplicationInfo> = arrayListOf()

	private lateinit var mPrefs: SharedPreferences
	private lateinit var mEditor: Editor
    private lateinit var binding: ActivityMainBinding

    private var progressDialog //deprecated, need to replace this later
			: ProgressDialog? = null
	private var appUninstallCancelled = false
	private var systemAppUninstalled = false

    private lateinit var db : AppDatabase
    private var uadRecords: Map<String, UadPackageRecord> = emptyMap()
    private lateinit var restoreRepository: RestoreRepository
    private var selectedUninstallMode: UninstallMode = UninstallMode.RESTORABLE
    private var uninstallBatchActive = false
    private var uninstallHasRootAccess = false
    private var pendingRestorePoint: RestorePoint? = null
    private var batchSuccessCount = 0
    private var batchFailureCount = 0

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        val mRvAppList = findViewById<RecyclerView>(R.id.rv_applist)
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this)
		mEditor = mPrefs.edit()
		//initialization
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "profiledb"
        ).addMigrations(AppDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        restoreRepository = RestoreRepository(applicationContext, db)
        selectedUninstallMode = UninstallMode.fromPreference(mPrefs.getString(UninstallMode.PREFERENCE_KEY, null))
        //Bad practice to allow main thread queries but db should be small,
        //its a headache to now support co routines for a simple db operation
		appUninstallCancelled = false

		//day night mode
		when {
			mPrefs.getInt("night_mode", 0) == 0 //auto
			-> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
			mPrefs.getInt("night_mode", 0) == 1 //day
			-> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
			else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
		}


		//toolbar icons visibility
		binding.toolbar.tvFreeSize.visibility = View.INVISIBLE
		binding.toolbar.imgbtnBack.visibility = View.GONE
        binding.toolbar.imgbtnDelete.visibility = View.GONE
        binding.toolbar.root.popupTheme = R.style.AppTheme
		setSupportActionBar(binding.toolbar.toolbar)

		//show dialog for root and non-root phones. If root ask for permission
		if (!mPrefs.getBoolean("ask_again", false)) {
			MaterialDialog.Builder(this@MainActivity)
					.title(R.string.important)
					.content(R.string.root_grant_ask)
					.neutralText(R.string.understood)
					.onAny { dialog, _ ->
						mEditor.putBoolean("ask_again", dialog.isPromptCheckBoxChecked)
						mEditor.apply()
						try {
							when {
								RootTools.isAccessGiven() -> Toast.makeText(applicationContext, R.string.granted, Toast.LENGTH_SHORT).show()
								else -> Toast.makeText(applicationContext, R.string.denied, Toast.LENGTH_SHORT).show()
							}
						} catch (e: Exception) {
							Log.e(javaClass.simpleName, "UnhandledException", e)
						}
					}
					.checkBoxPromptRes(R.string.dont_show_again, false, null)
					.show()
		}

        //populate lists with installed apps and details
        mPkgs = PackageUtils.getPackageNames(applicationContext)
        Log.d(TAG, "All-> " + mPkgs.size)
        for (pkg in mPkgs) {
            mApps.add(AppInfo(pkg, this))
        }
        //sort by installed data descending as default
        mApps.sortWith(Comparator { o1: AppInfo, o2: AppInfo -> if (o1.firstInstallTime < o2.firstInstallTime) 1 else -1 })
        allApps = ArrayList(mApps)
        mAppsCopy = ArrayList(mApps)

        //populate the recyclerview
        mAdapter = AppInfoAdapter(this, mApps) { position -> onAppRowClicked(position) }
        binding.searchApps.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchQuery = query.orEmpty().trim()
                applyAppFilters()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText.orEmpty().trim()
                applyAppFilters()
                return true
            }
        })
        loadDebloatDatabase()
        mRvAppList.layoutManager = LinearLayoutManager(this)
        mRvAppList.adapter = mAdapter
        mRvAppList.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))
        binding.toolbar.imgbtnBack.setOnClickListener {
            mFreeApps.clear()
            systemAppUninstalled = false
            binding.toolbar.imgbtnBack.visibility = View.GONE
            binding.toolbar.tvFreeSize.visibility = View.INVISIBLE
            binding.fabSort.visibility = View.VISIBLE
            binding.fabFilter.visibility = View.VISIBLE
            binding.toolbar.imgbtnDelete.visibility = View.GONE
            for (i in mApps.indices) mApps[i].color = R.color.backgroundPrimary
            mAdapter!!.notifyDataSetChanged()
        }
        binding.toolbar.imgbtnDelete.setOnClickListener { showUninstallConfirmation() }
        binding.layoutSwiperefresh.setOnRefreshListener { refreshList() }
        binding.fabFilter.setOnClickListener {
            MaterialDialog.Builder(this@MainActivity)
                .title(R.string.filter)
                .items(R.array.filter)
                .itemsCallbackSingleChoice(appFilter) { _, _, which, _ ->
                    if (which >= 0) {
                        appFilter = which
                        applyAppFilters()
                    }
                    true
                }
                .positiveText(R.string.done)
                .show()
        }
        binding.fabSort.setOnClickListener {
            MaterialDialog.Builder(this@MainActivity)
                    .title(R.string.sort)
                    .items(R.array.sort)
                    .itemsCallbackSingleChoice(-1) { _, _, which, _ ->
                        when (which) {
                            0 -> {
                                mApps.sortWith(Comparator { o1: AppInfo, o2: AppInfo -> o1.appName.compareTo(o2.appName, ignoreCase = true) })
                                mAdapter!!.notifyDataSetChanged()
                            }
                            1 -> {
                                mApps.sortWith(Comparator { o1: AppInfo, o2: AppInfo -> o2.appName.compareTo(o1.appName, ignoreCase = true) })
                                mAdapter!!.notifyDataSetChanged()
                            }
                            2 -> {
                                mApps.sortWith(Comparator { o1: AppInfo, o2: AppInfo -> if (o1.fileSize > o2.fileSize) 1 else -1 })
                                mAdapter!!.notifyDataSetChanged()
                            }
                            3 -> {
                                mApps.sortWith(Comparator { o1: AppInfo, o2: AppInfo -> if (o1.fileSize < o2.fileSize) 1 else -1 })
                                mAdapter!!.notifyDataSetChanged()
                            }
                            4 -> {
                                mApps.sortWith(Comparator { o1: AppInfo, o2: AppInfo -> if (o1.firstInstallTime < o2.firstInstallTime) -1 else 1 })
                                mAdapter!!.notifyDataSetChanged()
                            }
                            5 -> {
                                mApps.sortWith(Comparator { o1: AppInfo, o2: AppInfo -> if (o1.firstInstallTime < o2.firstInstallTime) 1 else -1 })
                                mAdapter!!.notifyDataSetChanged()
                            }
                            else -> {
                            }
                        }
                        true
                    }
                    .positiveText("Done")
                    .show()
        }
        mRvAppList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                when {
                    dy > 0 -> {
                        binding.fabSort.hide()
                        binding.fabFilter.hide()
                    }
                    dy < 0 && mFreeApps.size == 0 -> {
                        binding.fabSort.show()
                        binding.fabFilter.show()
                    }
                }
            }
        })
    }


    private fun onAppRowClicked(position: Int) {
        if (position !in mApps.indices) return
        val app = mApps[position]
        if (app.systemApp && !RootTools.isAccessGiven()) {
            Toast.makeText(applicationContext, R.string.no_root_system_app, Toast.LENGTH_SHORT).show()
            return
        }
        app.color = if (app.color == R.color.backgroundSelected) R.color.backgroundPrimary else R.color.backgroundSelected
        mAdapter?.notifyItemChanged(position)
        val info = app.info ?: return
        if (mFreeApps.contains(info)) mFreeApps.remove(info) else mFreeApps.add(info)

        if (mFreeApps.isEmpty()) {
            binding.toolbar.imgbtnBack.visibility = View.GONE
            binding.toolbar.imgbtnDelete.visibility = View.GONE
            binding.toolbar.tvFreeSize.visibility = View.INVISIBLE
            binding.fabSort.visibility = View.VISIBLE
            binding.fabFilter.visibility = View.VISIBLE
        } else {
            binding.toolbar.imgbtnBack.visibility = View.VISIBLE
            binding.toolbar.imgbtnDelete.visibility = View.VISIBLE
            binding.toolbar.tvFreeSize.visibility = View.VISIBLE
            binding.fabSort.visibility = View.INVISIBLE
            binding.fabFilter.visibility = View.INVISIBLE
            val totalBytes = mFreeApps.sumOf { PackageUtils.getApkSize(applicationContext, it.packageName) }
            binding.toolbar.tvFreeSize.text = Formatter.formatShortFileSize(applicationContext, totalBytes)
        }
    }

    fun uninstallAppsFromProfile(freeAppsFromProfile: ArrayList<ApplicationInfo>) {
        if (freeAppsFromProfile.isEmpty()) {
            Toast.makeText(this, R.string.uninstall_mode_no_apps, Toast.LENGTH_SHORT).show()
            return
        }
        mFreeApps = ArrayList(freeAppsFromProfile)
        moveBatchUninstallerToEnd(mFreeApps)
        showUninstallConfirmation()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1 || !uninstallBatchActive || uninstallHasRootAccess) return

        val current = mFreeApps.firstOrNull()
        if (current == null) {
            finishUninstallBatch()
            return
        }
        val backup = pendingRestorePoint
        pendingRestorePoint = null

        if (resultCode == Activity.RESULT_OK) {
            batchSuccessCount++
            removeInstalledAppFromLists(current.packageName)
            if (selectedUninstallMode == UninstallMode.PERMANENT) recordPermanentRemoval(current.packageName)
        } else {
            batchFailureCount++
            resetSelectedColor(current.packageName)
        }

        val continueBatch: () -> Unit = {
            if (mFreeApps.firstOrNull()?.packageName == current.packageName) mFreeApps.removeAt(0)
            progressDialog?.let { if (it.isShowing) it.incrementProgressBy(1) }
            if (appUninstallCancelled) finishUninstallBatch() else launchNextNonRootUninstall()
        }
        if (resultCode != Activity.RESULT_OK && backup != null) {
            Thread {
                val removed = restoreRepository.deleteBackup(backup)
                if (!removed) Log.e(TAG, "Could not clean up the cancelled restore point for ${current.packageName}")
                runOnUiThread { continueBatch() }
            }.start()
        } else {
            continueBatch()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.update_database -> {
                refreshDebloatDatabase(true)
                true
            }
            R.id.scan_apps_now -> {
                refreshList()
                true
            }
            R.id.database_interval -> {
                MaterialDialog.Builder(this@MainActivity)
                    .title(R.string.database_update_frequency)
                    .items(R.array.database_update_frequency_options)
                    .itemsCallbackSingleChoice(-1) { _, _, which, _ ->
                        val days = when (which) { 0 -> 1; 1 -> 3; 2 -> 7; else -> null }
                        UadDatabaseScheduler.setInterval(applicationContext, days)
                        true
                    }
                    .positiveText(R.string.done)
                    .show()
                true
            }
            R.id.about -> {
                about()
                true
            }
            R.id.nightmode -> {
                selectNightMode()
                true
            }
            R.id.feedback -> {
                feedback()
                true
            }
            R.id.rate -> {
                rate()
                true
            }
            R.id.profiles -> {
                val profileDialogFragment = ProfileDialogFragment.newInstance(mAppsCopy, this@MainActivity, db)
                profileDialogFragment.show(supportFragmentManager, ProfileDialogFragment.TAG)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun selectNightMode() {
        MaterialDialog.Builder(this@MainActivity)
                .title(R.string.night_mode)
                .items(R.array.night_mode_options)
                .itemsCallbackSingleChoice(-1) { _, _, which, _ ->
                    when (which) {
                        0, 1, 2 -> {
                            mEditor.putInt("night_mode", which)
                            mEditor.commit()
                            startActivity(intent)
                            finish()
                        }
                        else -> {
                        }
                    }
                    true
                }.show()
    }


    /**
     * Loads the last validated cache off the UI thread, then attempts an upstream refresh.
     * Database refresh and installed-package rescanning are intentionally separate actions.
     */
    private fun loadDebloatDatabase() {
        UadDatabaseScheduler.schedule(applicationContext)
        Thread {
            val repository = UadDatabaseRepository(applicationContext)
            val cached = repository.loadCached()
            if (cached != null) {
                uadRecords = cached.packages
                runOnUiThread { mAdapter?.updateDebloatEntries(uadRecords) }
            }
            val lastSuccess = repository.lastSuccessfulUpdateMillis()
            if (cached == null || System.currentTimeMillis() - lastSuccess >= 24L * 60L * 60L * 1000L) {
                try {
                    val fresh = repository.refresh()
                    uadRecords = fresh.packages
                    runOnUiThread { mAdapter?.updateDebloatEntries(uadRecords) }
                } catch (e: Exception) {
                    Log.w(TAG, "UAD-ng database refresh failed; retaining validated cache", e)
                }
            }
        }.start()
    }

    private fun refreshDebloatDatabase(showResult: Boolean) {
        Thread {
            try {
                val fresh = UadDatabaseRepository(applicationContext).refresh()
                uadRecords = fresh.packages
                runOnUiThread {
                    mAdapter?.updateDebloatEntries(uadRecords)
                    if (showResult) {
                        val message = if (fresh.fromCache) {
                            getString(R.string.database_up_to_date, fresh.packages.size)
                        } else {
                            getString(R.string.database_updated, fresh.packages.size)
                        }
                        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "UAD-ng database refresh failed", e)
                runOnUiThread {
                    if (showResult) {
                        val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.database_update_failed, detail.take(220)),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }.start()
    }


    private fun applyAppFilters() {
        val query = searchQuery
        val filtered = allApps.filter { app ->
            val matchesType = when (appFilter) {
                0 -> app.systemApp
                1 -> !app.systemApp
                else -> true
            }
            val matchesQuery = query.isBlank() ||
                app.appName.contains(query, ignoreCase = true) ||
                (app.packageName?.contains(query, ignoreCase = true) == true)
            matchesType && matchesQuery
        }
        mApps.clear()
        mApps.addAll(filtered)
        mAdapter?.notifyDataSetChanged()
    }

    private fun refreshList() {
        val thread: Thread = object : Thread() {
            override fun run() {
                mApps.clear()
                mPkgs = PackageUtils.getPackageNames(applicationContext)
                for (pkg in mPkgs) {
                    mApps.add(AppInfo(pkg, applicationContext))
                }
                //sort by installed data descending as default
                mApps.sortWith(Comparator { o1: AppInfo, o2: AppInfo -> if (o1.firstInstallTime < o2.firstInstallTime) 1 else -1 })
                allApps = ArrayList(mApps)
                mAppsCopy = ArrayList(allApps)
                runOnUiThread {
                    applyAppFilters()
                    mAdapter!!.notifyDataSetChanged()
                    binding.toolbar.imgbtnBack.visibility = View.GONE
                    binding.toolbar.imgbtnDelete.visibility = View.GONE
                    binding.toolbar.tvFreeSize.visibility = View.INVISIBLE
                    binding.fabSort.visibility = View.VISIBLE
                    binding.fabFilter.visibility = View.VISIBLE
                    mFreeApps.clear()
                    systemAppUninstalled = false
                    binding.layoutSwiperefresh.isRefreshing = false
                }
            }
        }
        thread.start()
    }

    private fun feedback() {
        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                "mailto", "sarbajitsaha1@gmail.com", null))
        try {
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.email_subject))
            startActivity(Intent.createChooser(emailIntent, resources.getString(R.string.send_email)))
        } catch (e: NotFoundException) {
            Log.e(javaClass.simpleName, "UnhandledException", e)
        }
    }

    private fun rate() {
        val uri = Uri.parse("market://details?id=$packageName")
        val goToMarket = Intent(Intent.ACTION_VIEW, uri)
        // TODO Fix version issue here
        goToMarket.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        try {
            startActivity(goToMarket)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
    }

    private fun about() {
        MaterialDialog.Builder(this@MainActivity)
                .title(R.string.about)
                .content(R.string.about_text)
                .positiveText(R.string.github)
                .negativeText(R.string.licenses)
                .onPositive { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/sarbajitsaha/Batch-Uninstaller"))
                    startActivity(intent)
                }
                .onNegative { _, _ ->
                    EasyLicensesDialogCompat(this@MainActivity)
                            .setTitle(R.string.licenses)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                }
                .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (mFreeApps.size == 0) super.onBackPressed() else {
            binding.toolbar.imgbtnBack.callOnClick()
        }
    }

    /**
     * Presents the two uninstall modes. Restorable mode is the default and requires a verified
     * APK backup for each app before any package-manager removal command is sent.
     */
    private fun showUninstallConfirmation() {
        if (mFreeApps.isEmpty()) {
            Toast.makeText(this, R.string.uninstall_mode_no_apps, Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_uninstall_mode, null, false)
        val summary = dialogView.findViewById<TextView>(R.id.tv_uninstall_summary)
        val restorableCard = dialogView.findViewById<MaterialCardView>(R.id.card_uninstall_restorable)
        val permanentCard = dialogView.findViewById<MaterialCardView>(R.id.card_uninstall_permanent)
        val restorableRadio = dialogView.findViewById<RadioButton>(R.id.radio_uninstall_restorable)
        val permanentRadio = dialogView.findViewById<RadioButton>(R.id.radio_uninstall_permanent)
        val permanentAck = dialogView.findViewById<CheckBox>(R.id.check_uninstall_permanent)
        val countInput = dialogView.findViewById<EditText>(R.id.input_uninstall_count)

        val names = mFreeApps.take(5).map { app ->
            try {
                app.loadLabel(packageManager).toString()
            } catch (_: Exception) {
                app.packageName
            }
        }
        val nameSummary = if (mFreeApps.size > 5) {
            getString(R.string.uninstall_summary_more, names.joinToString(", "), mFreeApps.size - 5)
        } else {
            names.joinToString(", ")
        }
        summary.text = getString(R.string.uninstall_selection_summary, mFreeApps.size, nameSummary)

        var choice = selectedUninstallMode
        var positiveButton: Button? = null
        fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
        fun renderChoice() {
            val isRestorable = choice == UninstallMode.RESTORABLE
            restorableRadio.isChecked = isRestorable
            permanentRadio.isChecked = !isRestorable
            restorableCard.strokeWidth = dp(if (isRestorable) 2 else 1)
            permanentCard.strokeWidth = dp(if (isRestorable) 1 else 2)
            restorableCard.strokeColor = ContextCompat.getColor(
                this, if (isRestorable) R.color.colorPrimary else R.color.colorOutline
            )
            permanentCard.strokeColor = ContextCompat.getColor(
                this, if (!isRestorable) R.color.colorError else R.color.colorOutline
            )
            restorableCard.setCardBackgroundColor(
                ContextCompat.getColor(this, if (isRestorable) R.color.backgroundSelected else R.color.colorSurface)
            )
            permanentCard.setCardBackgroundColor(
                ContextCompat.getColor(this, if (!isRestorable) R.color.debloat_unsafe_container else R.color.colorSurface)
            )

            val showPermanent = !isRestorable
            permanentAck.visibility = if (showPermanent) View.VISIBLE else View.GONE
            val requireTypedCount = showPermanent && mFreeApps.size >= 10
            countInput.visibility = if (requireTypedCount) View.VISIBLE else View.GONE
            positiveButton?.let { button ->
                button.text = getString(
                    if (isRestorable) R.string.uninstall_restorable_button else R.string.uninstall_permanent_button,
                    mFreeApps.size
                )
                val countMatches = !requireTypedCount || countInput.text.toString().trim() == mFreeApps.size.toString()
                button.isEnabled = isRestorable || (permanentAck.isChecked && countMatches)
            }
        }

        restorableCard.setOnClickListener { choice = UninstallMode.RESTORABLE; renderChoice() }
        permanentCard.setOnClickListener { choice = UninstallMode.PERMANENT; renderChoice() }
        permanentAck.setOnCheckedChangeListener { _, _ -> renderChoice() }
        countInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderChoice()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.uninstall_mode_dialog_title)
            .setView(dialogView)
            .setNegativeButton(R.string.no, null)
            .setPositiveButton(getString(R.string.uninstall_restorable_button, mFreeApps.size), null)
            .create()
        dialog.setOnShowListener {
            positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            renderChoice()
            positiveButton?.setOnClickListener {
                val needsCount = choice == UninstallMode.PERMANENT && mFreeApps.size >= 10
                val confirmed = choice == UninstallMode.RESTORABLE ||
                    (permanentAck.isChecked && (!needsCount || countInput.text.toString().trim() == mFreeApps.size.toString()))
                if (!confirmed) return@setOnClickListener
                selectedUninstallMode = choice
                mEditor.putString(UninstallMode.PREFERENCE_KEY, choice.name).apply()
                dialog.dismiss()
                beginUninstallBatch(choice)
            }
        }
        dialog.show()
    }

    private fun beginUninstallBatch(mode: UninstallMode) {
        if (uninstallBatchActive || mFreeApps.isEmpty()) return
        uninstallHasRootAccess = RootTools.isAccessGiven()
        if (!uninstallHasRootAccess && mFreeApps.any { isSystemApplication(it) }) {
            Toast.makeText(this, R.string.uninstall_mode_root_required, Toast.LENGTH_LONG).show()
            return
        }

        selectedUninstallMode = mode
        mEditor.putString(UninstallMode.PREFERENCE_KEY, mode.name).apply()
        moveBatchUninstallerToEnd(mFreeApps)
        batchSuccessCount = 0
        batchFailureCount = 0
        pendingRestorePoint = null
        systemAppUninstalled = false
        appUninstallCancelled = false
        uninstallBatchActive = true
        binding.layoutSwiperefresh.isEnabled = false
        showBatchProgress(if (mode == UninstallMode.RESTORABLE) R.string.uninstall_mode_prepare_backup else R.string.uninstall_trailing)

        if (mode == UninstallMode.RESTORABLE) {
            Thread {
                val preflight = try {
                    restoreRepository.preflightBatch(ArrayList(mFreeApps))
                } catch (e: Exception) {
                    com.saha.batchuninstaller.debloat.BackupPreflight(
                        false,
                        e.message ?: getString(R.string.restore_backup_folder_unavailable)
                    )
                }
                runOnUiThread {
                    if (!uninstallBatchActive) return@runOnUiThread
                    if (appUninstallCancelled) {
                        finishUninstallBatch()
                    } else if (!preflight.allowed) {
                        Toast.makeText(
                            this,
                            getString(R.string.uninstall_mode_space_failed, preflight.message.orEmpty()),
                            Toast.LENGTH_LONG
                        ).show()
                        finishUninstallBatch(showSummary = false)
                    } else {
                        startUninstallQueue()
                    }
                }
            }.start()
        } else {
            startUninstallQueue()
        }
    }

    private fun showBatchProgress(titleRes: Int) {
        progressDialog = ProgressDialog(this)
        progressDialog?.setTitle(titleRes)
        progressDialog?.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progressDialog?.max = mFreeApps.size
        progressDialog?.progress = 0
        progressDialog?.setCancelable(false)
        progressDialog?.setButton(
            DialogInterface.BUTTON_NEGATIVE,
            getString(R.string.cancel)
        ) { _, _ ->
            appUninstallCancelled = true
            progressDialog?.setMessage(getString(R.string.uninstall_batch_cancelled))
        }
        progressDialog?.show()
    }

    private fun startUninstallQueue() {
        if (!uninstallBatchActive || appUninstallCancelled) {
            finishUninstallBatch()
            return
        }
        progressDialog?.setTitle(R.string.uninstall_trailing)
        if (uninstallHasRootAccess) runRootUninstallNext() else launchNextNonRootUninstall()
    }

    private fun runRootUninstallNext() {
        if (!uninstallBatchActive) return
        if (appUninstallCancelled || mFreeApps.isEmpty()) {
            finishUninstallBatch()
            return
        }

        val application = mFreeApps.first()
        val packageName = application.packageName
        val label = try { application.loadLabel(packageManager).toString() } catch (_: Exception) { packageName }
        progressDialog?.setTitle(getString(R.string.uninstall_mode_preparing_app, label))

        Thread {
            var restorePoint: RestorePoint? = null
            var backupFailure: String? = null
            if (selectedUninstallMode == UninstallMode.RESTORABLE) {
                when (val result = restoreRepository.backupApp(application)) {
                    is RestoreBackupResult.Success -> restorePoint = result.point
                    is RestoreBackupResult.Failure -> backupFailure = result.reason
                }
            }

            val removed = backupFailure == null && application.uninstallAppRoot(selectedUninstallMode)
            if (!removed && restorePoint != null) {
                val cleaned = restoreRepository.deleteBackup(restorePoint!!)
                if (!cleaned) Log.e(TAG, "Could not clean up a failed-uninstall restore point for $packageName")
            }

            runOnUiThread {
                if (backupFailure != null) {
                    batchFailureCount++
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.uninstall_mode_backup_failed, label, backupFailure!!.take(180)),
                        Toast.LENGTH_LONG
                    ).show()
                } else if (removed) {
                    batchSuccessCount++
                    if (isSystemApplication(application)) systemAppUninstalled = true
                    removeInstalledAppFromLists(packageName)
                    if (selectedUninstallMode == UninstallMode.PERMANENT) recordPermanentRemoval(packageName)
                } else {
                    batchFailureCount++
                    resetSelectedColor(packageName)
                    Toast.makeText(this, getString(R.string.uninstall_mode_delete_failed, label), Toast.LENGTH_LONG).show()
                }

                if (mFreeApps.firstOrNull()?.packageName == packageName) mFreeApps.removeAt(0)
                progressDialog?.let { if (it.isShowing) it.incrementProgressBy(1) }
                if (appUninstallCancelled) finishUninstallBatch()
                else Handler(Looper.getMainLooper()).postDelayed({ runRootUninstallNext() }, 180L)
            }
        }.start()
    }

    private fun launchNextNonRootUninstall() {
        if (!uninstallBatchActive) return
        if (appUninstallCancelled || mFreeApps.isEmpty()) {
            finishUninstallBatch()
            return
        }

        val application = mFreeApps.first()
        val packageName = application.packageName
        if (isSystemApplication(application)) {
            batchFailureCount++
            resetSelectedColor(packageName)
            if (mFreeApps.firstOrNull()?.packageName == packageName) mFreeApps.removeAt(0)
            progressDialog?.let { if (it.isShowing) it.incrementProgressBy(1) }
            launchNextNonRootUninstall()
            return
        }

        if (selectedUninstallMode == UninstallMode.RESTORABLE) {
            val label = try { application.loadLabel(packageManager).toString() } catch (_: Exception) { packageName }
            progressDialog?.setTitle(getString(R.string.uninstall_mode_preparing_app, label))
            Thread {
                val result = restoreRepository.backupApp(application)
                runOnUiThread {
                    if (!uninstallBatchActive) return@runOnUiThread
                    when (result) {
                        is RestoreBackupResult.Success -> {
                            pendingRestorePoint = result.point
                            requestNonRootDelete(application)
                        }
                        is RestoreBackupResult.Failure -> {
                            batchFailureCount++
                            Toast.makeText(
                                this,
                                getString(R.string.uninstall_mode_backup_failed, label, result.reason.take(180)),
                                Toast.LENGTH_LONG
                            ).show()
                            if (mFreeApps.firstOrNull()?.packageName == packageName) mFreeApps.removeAt(0)
                            progressDialog?.let { if (it.isShowing) it.incrementProgressBy(1) }
                            if (appUninstallCancelled) finishUninstallBatch() else launchNextNonRootUninstall()
                        }
                    }
                }
            }.start()
        } else {
            requestNonRootDelete(application)
        }
    }

    private fun requestNonRootDelete(application: ApplicationInfo) {
        val packageUri = Uri.parse("package:" + application.packageName)
        val uninstallIntent = Intent(Intent.ACTION_DELETE, packageUri).apply {
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        try {
            startActivityForResult(uninstallIntent, 1)
        } catch (e: Exception) {
            batchFailureCount++
            val backup = pendingRestorePoint
            pendingRestorePoint = null
            Thread {
                if (backup != null) restoreRepository.deleteBackup(backup)
                runOnUiThread {
                    if (mFreeApps.firstOrNull()?.packageName == application.packageName) mFreeApps.removeAt(0)
                    progressDialog?.let { if (it.isShowing) it.incrementProgressBy(1) }
                    Toast.makeText(this, getString(R.string.uninstall_mode_delete_failed, application.packageName), Toast.LENGTH_LONG).show()
                    if (appUninstallCancelled) finishUninstallBatch() else launchNextNonRootUninstall()
                }
            }.start()
        }
    }

    private fun isSystemApplication(application: ApplicationInfo): Boolean =
        (application.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            application.sourceDir.startsWith("/system/") ||
            application.sourceDir.startsWith("/product/")

    private fun removeInstalledAppFromLists(packageName: String) {
        allApps.removeAll { it.packageName == packageName }
        mApps.removeAll { it.packageName == packageName }
        applyAppFilters()
    }

    private fun resetSelectedColor(packageName: String) {
        allApps.firstOrNull { it.packageName == packageName }?.color = R.color.backgroundPrimary
        mApps.firstOrNull { it.packageName == packageName }?.color = R.color.backgroundPrimary
        mAdapter?.notifyDataSetChanged()
    }

    private fun recordPermanentRemoval(packageName: String) {
        val prior = mPrefs.getString("permanent_uninstall_history", "").orEmpty()
            .lineSequence().filter { it.isNotBlank() }.toList()
        val updated = (prior + "${System.currentTimeMillis()}\t$packageName").takeLast(200)
        mEditor.putString("permanent_uninstall_history", updated.joinToString("\n")).apply()
    }

    private fun finishUninstallBatch(showSummary: Boolean = true) {
        if (!uninstallBatchActive) return
        uninstallBatchActive = false
        pendingRestorePoint = null
        progressDialog?.let { if (it.isShowing) it.dismiss() }
        progressDialog = null

        for (app in allApps) app.color = R.color.backgroundPrimary
        mFreeApps.clear()
        binding.toolbar.imgbtnBack.visibility = View.GONE
        binding.toolbar.imgbtnDelete.visibility = View.GONE
        binding.toolbar.tvFreeSize.visibility = View.INVISIBLE
        binding.fabSort.show()
        binding.fabFilter.show()
        binding.layoutSwiperefresh.isEnabled = true
        applyAppFilters()

        if (showSummary && (batchSuccessCount > 0 || batchFailureCount > 0)) {
            Toast.makeText(
                this,
                getString(R.string.uninstall_batch_result, batchSuccessCount, batchFailureCount),
                Toast.LENGTH_LONG
            ).show()
        }
        if (systemAppUninstalled && uninstallHasRootAccess) {
            MaterialDialog.Builder(this@MainActivity)
                .title(R.string.important)
                .content(R.string.system_app_reboot)
                .positiveText(R.string.reboot_now)
                .negativeText(R.string.reboot_later)
                .onPositive { _, _ -> if (RootTools.isAccessGiven()) RootTools.restartAndroid() }
                .show()
        }
        systemAppUninstalled = false
        appUninstallCancelled = false
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}