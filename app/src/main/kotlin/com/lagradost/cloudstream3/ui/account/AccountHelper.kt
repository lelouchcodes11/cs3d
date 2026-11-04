package com.lagradost.cloudstream3.ui.account

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.AccountEditDialogBinding
import com.lagradost.cloudstream3.databinding.AccountSelectLinearBinding
import com.lagradost.cloudstream3.databinding.BottomInputDialogBinding
import com.lagradost.cloudstream3.databinding.LockPinDialogBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getDefaultAccount
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideProgress
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import com.lagradost.cloudstream3.utils.UIHelper.showInputMethod
import com.lagradost.cloudstream3.utils.UIHelper.showProgress
import com.lagradost.cloudstream3.ui.account.AccountAdapter.Companion.setRippleForeground
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout

object AccountHelper {
    fun showAccountEditDialog(
        context: Context,
        account: DataStoreHelper.Account,
        isNewAccount: Boolean,
        accountEditCallback: (DataStoreHelper.Account) -> Unit,
        accountDeleteCallback: (DataStoreHelper.Account) -> Unit
    ) {
        val binding = AccountEditDialogBinding.inflate(LayoutInflater.from(context), null, false)
        val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setView(binding.root)

        var currentEditAccount = account
        val dialog = builder.show()
        binding.accountImageHolder.requestFocus()

        if (!isLayout(TV or EMULATOR)) {
            binding.accountImageHolder.setRippleForeground()
            binding.editProfilePhotoButtonHolder.setRippleForeground()
        }

        if (!isNewAccount) binding.title.setText(R.string.edit_account)

        // Set up the dialog content
        binding.accountName.text = Editable.Factory.getInstance()?.newEditable(account.name)
        binding.accountName.doOnTextChanged { text, _, _, _ ->
            currentEditAccount = currentEditAccount.copy(name = text?.toString() ?: "")
        }

        binding.deleteBtt.isGone = isNewAccount
        binding.deleteBtt.setOnClickListener {
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        accountDeleteCallback.invoke(account)
                        dialog?.dismissSafe()
                    }

                    DialogInterface.BUTTON_NEGATIVE -> {
                        dialog?.dismissSafe()
                    }
                }
            }

            try {
                AlertDialog.Builder(context).setTitle(R.string.delete).setMessage(
                    context.getString(R.string.delete_message).format(
                        currentEditAccount.name
                    )
                )
                    .setPositiveButton(R.string.delete, dialogClickListener)
                    .setNegativeButton(R.string.cancel, dialogClickListener)
                    .show().setDefaultFocus()
            } catch (t: Throwable) {
                logError(t)
            }
        }

        binding.cancelBtt.setOnClickListener {
            dialog?.dismissSafe()
        }

        // Handle the profile picture and its interactions
        binding.accountImage.loadImage(account.image)
        binding.accountImageHolder.setOnClickListener {
            // Roll the image forwards once
            currentEditAccount = currentEditAccount.copy(customImage = null)
            currentEditAccount =
                currentEditAccount.copy(defaultImageIndex = (currentEditAccount.defaultImageIndex + 1) % DataStoreHelper.profileImages.size)
            binding.accountImage.loadImage(currentEditAccount.image)
        }

        // Handle applying changes
        binding.applyBtt.setOnClickListener {
            if (currentEditAccount.lockPin != null) {
                // Ask for the current PIN
                showPinInputDialog(context, currentEditAccount.lockPin, false) { pin ->
                    if (pin == null) return@showPinInputDialog
                    // PIN is correct, proceed to update the account
                    accountEditCallback.invoke(currentEditAccount)
                    dialog.dismissSafe()
                }
            } else {
                // No lock PIN set, proceed to update the account
                accountEditCallback.invoke(currentEditAccount)
                dialog.dismissSafe()
            }
        }

        // Handle setting or changing the PIN
        if (currentEditAccount.keyIndex == getDefaultAccount(context).keyIndex) {
            binding.lockProfileCheckbox.isVisible = false
            if (currentEditAccount.lockPin != null) {
                currentEditAccount = currentEditAccount.copy(lockPin = null)
            }
        }

        var canSetPin = true

        binding.lockProfileCheckbox.isChecked = currentEditAccount.lockPin != null

        binding.lockProfileCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (canSetPin) {
                    showPinInputDialog(context, null, true) { pin ->
                        if (pin == null) {
                            binding.lockProfileCheckbox.isChecked = false
                            return@showPinInputDialog
                        }

                        currentEditAccount = currentEditAccount.copy(lockPin = pin)
                    }
                }
            } else {
                if (currentEditAccount.lockPin != null) {
                    // Ask for the current PIN
                    showPinInputDialog(context, currentEditAccount.lockPin, true) { pin ->
                        if (pin == null || pin != currentEditAccount.lockPin) {
                            canSetPin = false
                            binding.lockProfileCheckbox.isChecked = true
                        } else {
                            currentEditAccount = currentEditAccount.copy(lockPin = null)
                        }
                    }
                }
            }
        }

        canSetPin = true

        binding.editProfilePhotoButtonHolder.setOnClickListener {
            val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
            val sheetBinding = BottomInputDialogBinding.inflate(LayoutInflater.from(context))
            builder.setView(sheetBinding.root)
            val dialog = builder.create()
            dialog.show()

            sheetBinding.apply {
                text1.text = context.getString(R.string.edit_profile_image_title)
                nginxTextInput.hint = context.getString(R.string.edit_profile_image_hint)

                applyBtt.setOnClickListener {
                    val url = sheetBinding.nginxTextInput.text.toString()
                    if (url.isEmpty()) {
                        showToast(R.string.edit_profile_image_error_empty, Toast.LENGTH_SHORT)
                        return@setOnClickListener
                    }
                    applyBtt.showProgress()
                    val imageLoader = ImageLoader(context)
                    val request = ImageRequest.Builder(coil3.PlatformContext.INSTANCE)
                        .data(url)
                        .allowHardware(false)
                        .listener(
                            onSuccess = { _, _ ->
                                currentEditAccount = currentEditAccount.copy(customImage = url)
                                binding.accountImage.loadImage(url)
                                showToast(
                                    R.string.edit_profile_image_success,
                                    Toast.LENGTH_SHORT
                                )
                                dialog.dismissSafe()
                            },
                            onError = { _, _ ->
                                showToast(
                                    R.string.edit_profile_image_error_invalid,
                                    Toast.LENGTH_SHORT
                                )
                                applyBtt.hideProgress()
                            },
                            onCancel = {
                                applyBtt.hideProgress()
                            }
                        )
                        .build()
                    imageLoader.enqueue(request)
                }
                sheetBinding.cancelBtt.setOnClickListener {
                    dialog.dismissSafe()
                }
            }
        }
    }

    fun showPinInputDialog(
        context: Context,
        currentPin: String?,
        editAccount: Boolean,
        forStartup: Boolean = false,
        errorText: String? = null,
        callback: (String?) -> Unit
    ) {
        // the desktop asks on its own PIN pad; at the start a cancel goes to the profile picker on an unlocked profile (never the locked one)
        val owner = DataStoreHelper.accounts.filter { currentPin != null && it.lockPin == currentPin }.singleOrNull()?.name
        val title = when {
            editAccount && currentPin == null -> "Set a PIN"
            owner != null -> "PIN for $owner"
            else -> "Enter the PIN"
        }
        if (forStartup) {
            val other = {
                com.lagradost.desktop.ui.shell.PinLock.leaveLocked()
                com.lagradost.desktop.ui.shell.showAccountPicker(forStartup = true)
            }
            com.lagradost.desktop.ui.shell.PinLock.ask(title, currentPin, other) { pin -> if (pin == null) other() else callback(pin) }
        } else {
            com.lagradost.desktop.ui.shell.PinLock.ask(title, currentPin, null, callback)
        }
    }

    fun Activity?.showAccountSelectLinear() {
        val activity = this as? MainActivity ?: return
        val viewModel = ViewModelProvider(activity)[AccountViewModel::class.java]

        val binding: AccountSelectLinearBinding = AccountSelectLinearBinding.inflate(
            LayoutInflater.from(activity)
        )

        val builder = BottomSheetDialog(activity)
        builder.setContentView(binding.root)
        builder.show()

        binding.manageAccountsButton.setOnClickListener {
            activity.navigate(
                R.id.accountSelectActivity,
                Bundle().apply { putBoolean("isEditingFromMainActivity", true) }
            )
            builder.dismissSafe()
        }

        val recyclerView: RecyclerView = binding.accountRecyclerView

        val itemSize = recyclerView.resources.getDimensionPixelSize(
            R.dimen.account_select_linear_item_size
        )

        recyclerView.addItemDecoration(AccountSelectLinearItemDecoration(itemSize))

        recyclerView.setLinearListLayout(isHorizontal = true)

        val currentAccount = DataStoreHelper.accounts.firstOrNull {
            it.keyIndex == DataStoreHelper.selectedKeyIndex
        } ?: getDefaultAccount(activity)

        // We want to make sure the accounts are up-to-date
        viewModel.handleAccountSelect(
            currentAccount,
            activity,
            reloadForActivity = true
        )

        activity.observe(viewModel.accounts) { liveAccounts ->
            recyclerView.adapter = AccountAdapter(
                accountSelectCallback = { account ->
                    viewModel.handleAccountSelect(account, activity)
                    builder.dismissSafe()
                },
                accountCreateCallback = { viewModel.handleAccountUpdate(it, activity) },
                accountEditCallback = { viewModel.handleAccountUpdate(it, activity) },
                accountDeleteCallback = { viewModel.handleAccountDelete(it, activity) }
            ).apply {
                submitList(liveAccounts)
            }

            activity.observe(viewModel.selectedKeyIndex) { selectedKeyIndex ->
                // Scroll to current account (which is focused by default)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                layoutManager.scrollToPositionWithOffset(selectedKeyIndex, 0)
            }
        }
    }
}