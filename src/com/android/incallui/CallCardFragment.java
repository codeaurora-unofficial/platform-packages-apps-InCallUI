/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.incallui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.provider.Telephony.Sms;
import android.telecom.DisconnectCause;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.text.format.DateUtils;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import android.telecom.AudioState;
import android.telecom.VideoProfile;
import com.android.incallui.RcsApiManager;
import com.android.contacts.common.widget.FloatingActionButtonController;
import com.android.internal.telephony.util.BlacklistUtils;
import com.android.phone.common.animation.AnimUtils;

import java.util.List;

/**
 * Fragment for call card.
 */
public class CallCardFragment extends BaseFragment<CallCardPresenter, CallCardPresenter.CallCardUi>
        implements CallCardPresenter.CallCardUi {

    private AnimatorSet mAnimatorSet;
    private int mRevealAnimationDuration;
    private int mShrinkAnimationDuration;
    private int mFabNormalDiameter;
    private int mFabSmallDiameter;
    private boolean mIsLandscape;
    private boolean mIsDialpadShowing;

    // Primary caller info
    private TextView mPhoneNumber;
    private TextView mNumberLabel;
    private TextView mPrimaryName;
    private View mCallStateButton;
    private ImageView mCallStateIcon;
    private ImageView mCallStateVideoCallIcon;
    private TextView mCallStateLabel;
    private TextView mCallTypeLabel;
    private View mCallNumberAndLabel;
    private ImageView mPhoto;
    private TextView mElapsedTime;
    private View mSendMessageView;
    private TextView mUnreadMessageCount;
    private ImageButton mMoreMenuButton;
    private MorePopupMenu mMoreMenu;

    // Container view that houses the entire primary call card, including the call buttons
    private View mPrimaryCallCardContainer;
    // Container view that houses the primary call information
    private ViewGroup mPrimaryCallInfo;
    private View mCallButtonsContainer;
    private ImageButton mVBButton;
    private ImageButton mSwitchCamera;
    private AudioManager mAudioManager;
    private Toast mVBNotify;
    private int mVBToastPosition;
    private TextView mRecordingTimeLabel;
    private TextView mRecordingIcon;

    // Secondary caller info
    private View mSecondaryCallInfo;
    private TextView mSecondaryCallName;
    private View mSecondaryCallProviderInfo;
    private TextView mSecondaryCallProviderLabel;
    private ImageView mSecondaryCallProviderIcon;
    private View mSecondaryCallConferenceCallIcon;
    private View mProgressSpinner;

    private View mManageConferenceCallButton;

    private Dialog mModifyCallPromptDialog = null;

    // Dark number info bar
    private TextView mInCallMessageLabel;

    private FloatingActionButtonController mFloatingActionButtonController;
    private View mFloatingActionButtonContainer;
    private ImageButton mFloatingActionButton;
    private int mFloatingActionButtonVerticalOffset;

    // Cached DisplayMetrics density.
    private float mDensity;

    private float mTranslationOffset;
    private Animation mPulseAnimation;

    private int mVideoAnimationDuration;

    private String mRecordingTime;

    private static final int TTY_MODE_OFF = 0;
    private static final int TTY_MODE_HCO = 2;

    private static final String VOLUME_BOOST = "volume_boost";

    private static final String RECORD_STATE_CHANGED =
            "com.qualcomm.qti.phonefeature.RECORD_STATE_CHANGED";

    private static final int MESSAGE_TIMER = 1;

    private InCallActivity mInCallActivity;
    // for RCS
    private RcsRichScreen mRcsRichScreen = null;
    private boolean misEhanceScreenApkInstalled = false;
    private boolean mIsRcsServiceInstalled = false;
    private static final String ENHANCE_SCREEN_APK_NAME = "com.cmdm.rcs";
    private static final String LOG_TAG = "RCS_UI";
    //RCS end

    private static final int DEFAULT_VIEW_OFFSET_Y = 0;

    @Override
    CallCardPresenter.CallCardUi getUi() {
        return this;
    }

    @Override
    CallCardPresenter createPresenter() {
        return new CallCardPresenter();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRevealAnimationDuration = getResources().getInteger(R.integer.reveal_animation_duration);
        mShrinkAnimationDuration = getResources().getInteger(R.integer.shrink_animation_duration);
        mVideoAnimationDuration = getResources().getInteger(R.integer.video_animation_duration);
        mFloatingActionButtonVerticalOffset = getResources().getDimensionPixelOffset(
                R.dimen.floating_action_bar_vertical_offset);
        mFabNormalDiameter = getResources().getDimensionPixelOffset(
                R.dimen.end_call_floating_action_button_diameter);
        mFabSmallDiameter = getResources().getDimensionPixelOffset(
                R.dimen.end_call_floating_action_button_small_diameter);

        mVBToastPosition = Integer.parseInt(
                getResources().getString(R.string.volume_boost_toast_position));

        mAudioManager = (AudioManager) getActivity()
                .getSystemService(Context.AUDIO_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(RECORD_STATE_CHANGED);
        getActivity().registerReceiver(recorderStateReceiver, filter);

        mInCallActivity = (InCallActivity)getActivity();
        misEhanceScreenApkInstalled = isEnhanceScreenInstalled();
        mIsRcsServiceInstalled = RcsApiManager.isRcsServiceInstalled();
        if (mInCallActivity.isCallRecording()) {
            recorderHandler.sendEmptyMessage(MESSAGE_TIMER);
        }
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final CallList calls = CallList.getInstance();
        final Call call = calls.getFirstCall();
        getPresenter().init(getActivity(), call);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        mDensity = getResources().getDisplayMetrics().density;
        mTranslationOffset =
                getResources().getDimensionPixelSize(R.dimen.call_card_anim_translate_y_offset);
        if(!isRcsAvailable()){
            return inflater.inflate(R.layout.call_card_content, container, false);
        } else {
            return inflater.inflate(R.layout.rcs_call_card_content, container, false);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPulseAnimation =
                AnimationUtils.loadAnimation(view.getContext(), R.anim.call_status_pulse);

        mPhoneNumber = (TextView) view.findViewById(R.id.phoneNumber);
        mPrimaryName = (TextView) view.findViewById(R.id.name);
        mNumberLabel = (TextView) view.findViewById(R.id.label);
        mSecondaryCallInfo = view.findViewById(R.id.secondary_call_info);
        mSecondaryCallProviderInfo = view.findViewById(R.id.secondary_call_provider_info);
        mPhoto = (ImageView) view.findViewById(R.id.photo);
        mCallStateIcon = (ImageView) view.findViewById(R.id.callStateIcon);
        mCallStateVideoCallIcon = (ImageView) view.findViewById(R.id.videoCallIcon);
        mCallStateLabel = (TextView) view.findViewById(R.id.callStateLabel);
        mCallNumberAndLabel = view.findViewById(R.id.labelAndNumber);
        mCallTypeLabel = (TextView) view.findViewById(R.id.callTypeLabel);
        mElapsedTime = (TextView) view.findViewById(R.id.elapsedTime);
        mPrimaryCallCardContainer = view.findViewById(R.id.primary_call_info_container);
        mPrimaryCallInfo = (ViewGroup) view.findViewById(R.id.primary_call_banner);
        mCallButtonsContainer = view.findViewById(R.id.callButtonFragment);
        mInCallMessageLabel = (TextView) view.findViewById(R.id.connectionServiceMessage);
        mProgressSpinner = view.findViewById(R.id.progressSpinner);

        if (isRcsAvailable()) {
            TextView rcsmissdnAddress = (TextView)view.findViewById(R.id.missdnaddress);
            TextView rcsgreeting = (TextView)view.findViewById(R.id.greeting);
            SurfaceView rcssurface = (SurfaceView)view.findViewById(R.id.surface);
            ImageView rcsPhoto = (ImageView) view.findViewById(R.id.rcs_photo);
            GifMovieView rcsGifMovieView = (GifMovieView) view.findViewById(R.id.incallgifview);
            mRcsRichScreen = new RcsRichScreen(getActivity(),
                rcsPhoto, rcsgreeting, rcsmissdnAddress, rcsGifMovieView, rcssurface);
        }
        if (mIsRcsServiceInstalled) {
            mSendMessageView = view.findViewById(R.id.sendMessage);
            mSendMessageView.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    getPresenter().sendSmsClicked();
                }
            });
            mUnreadMessageCount = (TextView) view.findViewById(R.id.unreadMessageCount);
            updateUnReadSmsCount();
        } else {
            mSendMessageView = view.findViewById(R.id.sendMessage);
            mSendMessageView.setVisibility(View.GONE);
        }
        mMoreMenuButton = (ImageButton) view.findViewById(R.id.moreMenuButton);
        final ContextThemeWrapper contextWrapper = new ContextThemeWrapper(getActivity(),
                R.style.InCallPopupMenuStyle);
        mMoreMenu = new MorePopupMenu(contextWrapper, mMoreMenuButton /* anchorView */);
        mMoreMenu.getMenuInflater().inflate(R.menu.incall_more_menu, mMoreMenu.getMenu());
        mMoreMenuButton.setOnTouchListener(mMoreMenu.getDragToOpenListener());
        mMoreMenuButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Call call = CallList.getInstance().getActiveOrBackgroundCall();
                if (call != null) {
                    updateMoreMenuByCall(call.getState());
                }
                mMoreMenu.show();
            }
        });

        mFloatingActionButtonContainer = view.findViewById(
                R.id.floating_end_call_action_button_container);
        mFloatingActionButton = (ImageButton) view.findViewById(
                R.id.floating_end_call_action_button);
        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().endCallClicked();
            }
        });
        mFloatingActionButtonController = new FloatingActionButtonController(getActivity(),
                mFloatingActionButtonContainer, mFloatingActionButton);

        mSecondaryCallInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().secondaryInfoClicked();
                updateFabPositionForSecondaryCallInfo();
            }
        });

        mCallStateButton = view.findViewById(R.id.callStateButton);
        mCallStateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().onCallStateButtonTouched();
            }
        });

        mManageConferenceCallButton = view.findViewById(R.id.manage_conference_call_button);
        mManageConferenceCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InCallActivity activity = (InCallActivity) getActivity();
                activity.showConferenceCallManager();
            }
        });

        mPrimaryName.setElegantTextHeight(false);
        mCallStateLabel.setElegantTextHeight(false);

        mVBButton = (ImageButton) view.findViewById(R.id.volumeBoost);
        if (null != mVBButton) {
            mVBButton.setOnClickListener(mVBListener);
        }
        mSwitchCamera = (ImageButton) view.findViewById(R.id.switchCamera);
        if (null != mSwitchCamera) {
            mSwitchCamera.setOnClickListener(mSwitchCameraListener);
        }
        mRecordingTimeLabel = (TextView) view.findViewById(R.id.recordingTime);
        mRecordingIcon = (TextView) view.findViewById(R.id.recordingIcon);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Sms.Intents.SMS_RECEIVED_ACTION);
        mInCallActivity.registerReceiver(mSmsReceiver, filter);
    }

    private void updateUnReadSmsCount() {
        if (!mIsRcsServiceInstalled) {
            return;
        }
        final Handler handler = new Handler();
        Thread t = new Thread() {
            @Override
            public void run() {
                final int unRead = getPresenter().getUnReadMessageCount(mInCallActivity);
                Log.d(LOG_TAG, "CallCardFragment: updateUnReadMessageCount(" + unRead + ")");
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        setUnReadMessageCount(unRead);
                    }
                });
            }
        };
        t.start();
    }

    public void onDestroyView() {
        mInCallActivity.unregisterReceiver(mSmsReceiver);
        super.onDestroyView();
    }

    @Override
    public void setVisible(boolean on) {
        if (on) {
            getView().setVisibility(View.VISIBLE);
        } else {
            getView().setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Hides or shows the progress spinner.
     *
     * @param visible {@code True} if the progress spinner should be visible.
     */
    @Override
    public void setProgressSpinnerVisible(boolean visible) {
        mProgressSpinner.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Sets the visibility of the primary call card.
     * Ensures that when the primary call card is hidden, the video surface slides over to fill the
     * entire screen.
     *
     * @param visible {@code True} if the primary call card should be visible.
     */
    @Override
    public void setCallCardVisible(final boolean visible) {
        // When animating the hide/show of the views in a landscape layout, we need to take into
        // account whether we are in a left-to-right locale or a right-to-left locale and adjust
        // the animations accordingly.
        final boolean isLayoutRtl = InCallPresenter.isRtl();

        // Retrieve here since at fragment creation time the incoming video view is not inflated.
        final View videoView = getView().findViewById(R.id.incomingVideo);

        // Determine how much space there is below or to the side of the call card.
        final float spaceBesideCallCard = getSpaceBesideCallCard();

        // We need to translate the video surface, but we need to know its position after the layout
        // has occurred so use a {@code ViewTreeObserver}.
        final ViewTreeObserver observer = getView().getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                // We don't want to continue getting called.
                if (observer.isAlive()) {
                    observer.removeOnPreDrawListener(this);
                }

                float videoViewTranslation = 0f;

                // Translate the call card to its pre-animation state.
                if (mIsLandscape) {
                    float translationX = mPrimaryCallCardContainer.getWidth();
                    translationX *= isLayoutRtl ? 1 : -1;

                    mPrimaryCallCardContainer.setTranslationX(visible ? translationX : 0);

                    if (visible) {
                        videoViewTranslation = videoView.getWidth() / 2 - spaceBesideCallCard / 2;
                        videoViewTranslation *= isLayoutRtl ? -1 : 1;
                    }
                } else {
                    mPrimaryCallCardContainer.setTranslationY(visible ?
                            -mPrimaryCallCardContainer.getHeight() : 0);

                    if (visible) {
                        videoViewTranslation = videoView.getHeight() / 2 - spaceBesideCallCard / 2;
                    }
                }

                // Perform animation of video view.
                ViewPropertyAnimator videoViewAnimator = videoView.animate()
                        .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
                        .setDuration(mVideoAnimationDuration);
                if (mIsLandscape) {
                    videoViewAnimator
                            .translationX(videoViewTranslation)
                            .start();
                } else {
                    videoViewAnimator
                            .translationY(videoViewTranslation)
                            .start();
                }
                videoViewAnimator.start();

                // Animate the call card sliding.
                ViewPropertyAnimator callCardAnimator = mPrimaryCallCardContainer.animate()
                        .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
                        .setDuration(mVideoAnimationDuration)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                if (!visible) {
                                    mPrimaryCallCardContainer.setVisibility(View.GONE);
                                }
                            }

                            @Override
                            public void onAnimationStart(Animator animation) {
                                super.onAnimationStart(animation);
                                if (visible) {
                                    mPrimaryCallCardContainer.setVisibility(View.VISIBLE);
                                }
                            }
                        });

                if (mIsLandscape) {
                    float translationX = mPrimaryCallCardContainer.getWidth();
                    translationX *= isLayoutRtl ? 1 : -1;
                    callCardAnimator
                            .translationX(visible ? 0 : translationX)
                            .start();
                } else {
                    callCardAnimator
                            .translationY(visible ? 0 : -mPrimaryCallCardContainer.getHeight())
                            .start();
                }

                return true;
            }
        });
    }

    public void setUnReadMessageCount(int count) {
        if (null == mUnreadMessageCount) {
            return;
        }
        if (count > 0) {
            mUnreadMessageCount.setBackgroundResource(R.drawable.rcs_incall_message_count);
            mUnreadMessageCount.setText(String.valueOf(count));
        } else {
            mUnreadMessageCount.setBackgroundResource(R.drawable.rcs_incall_message);
            mUnreadMessageCount.setText("");
        }
    }

    /**
     * Determines the amount of space below the call card for portrait layouts), or beside the
     * call card for landscape layouts.
     *
     * @return The amount of space below or beside the call card.
     */
    public float getSpaceBesideCallCard() {
        if (mIsLandscape) {
            return getView().getWidth() - mPrimaryCallCardContainer.getWidth();
        } else {
            return getView().getHeight() - mPrimaryCallCardContainer.getHeight();
        }
    }

    @Override
    public void setPrimaryName(String name, boolean nameIsNumber) {
        if (TextUtils.isEmpty(name)) {
            mPrimaryName.setText(null);
        } else {
            mPrimaryName.setText(name);

            // Set direction of the name field
            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            mPrimaryName.setTextDirection(nameDirection);
        }
    }

    @Override
    public void setPrimaryImage(Drawable image) {
        if (image != null) {
            setDrawableToImageView(mPhoto, image);
        }
    }

    @Override
    public void setPrimaryPhoneNumber(String number) {
        // Set the number
        if (TextUtils.isEmpty(number)) {
            mPhoneNumber.setText(null);
            mPhoneNumber.setVisibility(View.GONE);
        } else {
            mPhoneNumber.setText(number);
            mPhoneNumber.setVisibility(View.VISIBLE);
            mPhoneNumber.setTextDirection(View.TEXT_DIRECTION_LTR);
        }
    }

    @Override
    public void setPrimaryLabel(String label) {
        if (!TextUtils.isEmpty(label)) {
            mNumberLabel.setText(label);
            mNumberLabel.setVisibility(View.VISIBLE);
        } else {
            mNumberLabel.setVisibility(View.GONE);
        }

    }

    @Override
    public void setPrimary(String number, String name, boolean nameIsNumber, String label,
            Drawable photo, boolean isConference, boolean canManageConference,
            boolean isSipCall, boolean isForwarded) {
        Log.d(this, "Setting primary call");

        // set the name field.
        setPrimaryName(name, nameIsNumber);

        if (TextUtils.isEmpty(number) && TextUtils.isEmpty(label)) {
            mCallNumberAndLabel.setVisibility(View.GONE);
        } else {
            mCallNumberAndLabel.setVisibility(View.VISIBLE);
        }

        setPrimaryPhoneNumber(number);

        // Set the label (Mobile, Work, etc)
        setPrimaryLabel(label);

        showCallTypeLabel(isSipCall, isForwarded);
        if (mRcsRichScreen != null && isRcsAvailable()) {
            String rcsnumber = null;
            if(!nameIsNumber){
                rcsnumber = number;
            } else {
                rcsnumber = name;
            }
            mRcsRichScreen.setNumber(rcsnumber);
        }
        setDrawableToImageView(mPhoto, photo);
    }

    @Override
    public void setSecondary(boolean show, String name, boolean nameIsNumber, String label,
            String providerLabel, Drawable providerIcon, boolean isConference) {

        if (show != mSecondaryCallInfo.isShown()) {
            updateFabPositionForSecondaryCallInfo();
        }

        if (show) {
            boolean hasProvider = !TextUtils.isEmpty(providerLabel);
            showAndInitializeSecondaryCallInfo(hasProvider);

            mSecondaryCallConferenceCallIcon.setVisibility(isConference ? View.VISIBLE : View.GONE);

            mSecondaryCallName.setText(name);
            if (hasProvider) {
                mSecondaryCallProviderLabel.setText(providerLabel);
                mSecondaryCallProviderIcon.setImageDrawable(providerIcon);
            }

            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            mSecondaryCallName.setTextDirection(nameDirection);
        } else {
            mSecondaryCallInfo.setVisibility(View.GONE);
        }
    }

    @Override
    public void setCallState(
            int state,
            int videoState,
            int sessionModificationState,
            DisconnectCause disconnectCause,
            String connectionLabel,
            Drawable callStateIcon,
            String gatewayNumber,
            boolean isWaitingForRemoteSide) {
        boolean isGatewayCall = !TextUtils.isEmpty(gatewayNumber);
        CharSequence callStateLabel = getCallStateLabelFromState(state, videoState,
                sessionModificationState, disconnectCause, connectionLabel,
                isGatewayCall, isWaitingForRemoteSide);

        updateVBbyCall(state, videoState);
        updateSwitchCameraByCall(state, videoState);
        updateMoreMenuByCall(state);

        Log.v(this, "setCallState " + callStateLabel);
        Log.v(this, "DisconnectCause " + disconnectCause.toString());
        Log.v(this, "gateway " + connectionLabel + gatewayNumber);

        if (TextUtils.equals(callStateLabel, mCallStateLabel.getText())) {
            // Nothing to do if the labels are the same
            return;
        }
        // update Rcs RichScreen by call state
        if (mRcsRichScreen != null && isRcsAvailable()) {
           mRcsRichScreen.updateRichScreenByCallState(state,videoState);
        }

        // Update the call state label and icon.
        if (!TextUtils.isEmpty(callStateLabel)) {
            mCallStateLabel.setText(callStateLabel);
            mCallStateLabel.setAlpha(1);
            mCallStateLabel.setVisibility(View.VISIBLE);

            if (state == Call.State.ACTIVE || state == Call.State.CONFERENCED) {
                mCallStateLabel.clearAnimation();
            } else {
                mCallStateLabel.startAnimation(mPulseAnimation);
            }
        } else {
            mCallStateLabel.clearAnimation();
            Animation callStateLabelAnimation = mCallStateLabel.getAnimation();
            if (callStateLabelAnimation != null) {
                callStateLabelAnimation.cancel();
            }
            mCallStateLabel.setText(null);
            mCallStateLabel.setAlpha(0);
            mCallStateLabel.setVisibility(View.GONE);
        }

        if (callStateIcon != null) {
            mCallStateIcon.setVisibility(View.VISIBLE);
            // Invoke setAlpha(float) instead of setAlpha(int) to set the view's alpha. This is
            // needed because the pulse animation operates on the view alpha.
            mCallStateIcon.setAlpha(1.0f);
            mCallStateIcon.setImageDrawable(callStateIcon);

            if (state == Call.State.ACTIVE || state == Call.State.CONFERENCED
                    || TextUtils.isEmpty(callStateLabel)) {
                mCallStateIcon.clearAnimation();
            } else {
                if (mCallStateIcon.getVisibility() == View.VISIBLE) {
                    mCallStateIcon.startAnimation(mPulseAnimation);
                }
            }
        } else {
            Animation callStateIconAnimation = mCallStateIcon.getAnimation();
            if (callStateIconAnimation != null) {
                callStateIconAnimation.cancel();
            }

            // Invoke setAlpha(float) instead of setAlpha(int) to set the view's alpha. This is
            // needed because the pulse animation operates on the view alpha.
            mCallStateIcon.setAlpha(0.0f);
            mCallStateIcon.clearAnimation();
            mCallStateIcon.setVisibility(View.GONE);
        }

        if (VideoProfile.VideoState.isBidirectional(videoState)
                || (state == Call.State.ACTIVE && sessionModificationState
                        == Call.SessionModificationState.WAITING_FOR_RESPONSE)) {
            mCallStateVideoCallIcon.setVisibility(View.VISIBLE);
        } else {
            mCallStateVideoCallIcon.setVisibility(View.GONE);
        }
    }

    @Override
    public void setCallbackNumber(String callbackNumber, boolean isEmergencyCall) {
        if (mInCallMessageLabel == null) {
            return;
        }

        if (TextUtils.isEmpty(callbackNumber)) {
            mInCallMessageLabel.setVisibility(View.GONE);
            return;
        }

        // TODO: The new Locale-specific methods don't seem to be working. Revisit this.
        callbackNumber = PhoneNumberUtils.formatNumber(callbackNumber);

        int stringResourceId = isEmergencyCall ? R.string.card_title_callback_number_emergency
                : R.string.card_title_callback_number;

        String text = getString(stringResourceId, callbackNumber);
        mInCallMessageLabel.setText(text);

        mInCallMessageLabel.setVisibility(View.VISIBLE);
    }

    private void showCallTypeLabel(boolean isSipCall, boolean isForwarded) {
        if (isSipCall) {
            mCallTypeLabel.setVisibility(View.VISIBLE);
            mCallTypeLabel.setText(R.string.incall_call_type_label_sip);
        } else if (isForwarded) {
            mCallTypeLabel.setVisibility(View.VISIBLE);
            mCallTypeLabel.setText(R.string.incall_call_type_label_forwarded);
        } else {
            mCallTypeLabel.setVisibility(View.GONE);
        }
    }

    @Override
    public void setPrimaryCallElapsedTime(boolean show, String callTimeElapsed) {
        if (show) {
            if (mElapsedTime.getVisibility() != View.VISIBLE) {
                AnimUtils.fadeIn(mElapsedTime, AnimUtils.DEFAULT_DURATION);
            }
            mElapsedTime.setText(callTimeElapsed);
        } else {
            // hide() animation has no effect if it is already hidden.
            AnimUtils.fadeOut(mElapsedTime, AnimUtils.DEFAULT_DURATION);
        }
    }

    private void setDrawableToImageView(ImageView view, Drawable photo) {
        if (photo == null) {
            photo = view.getResources().getDrawable(R.drawable.img_no_image);
            photo.setAutoMirrored(true);
        }

        final Drawable current = view.getDrawable();
        if (current == null) {
            view.setImageDrawable(photo);
            AnimUtils.fadeIn(mElapsedTime, AnimUtils.DEFAULT_DURATION);
        } else {
            InCallAnimationUtils.startCrossFade(view, current, photo);
            view.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Gets the call state label based on the state of the call or cause of disconnect.
     *
     * Additional labels are applied as follows:
     *         1. All outgoing calls with display "Calling via [Provider]".
     *         2. Ongoing calls will display the name of the provider.
     *         3. Incoming calls will only display "Incoming via..." for accounts.
     *         4. Video calls, and session modification states (eg. requesting video).
     */
    private CharSequence getCallStateLabelFromState(int state, int videoState,
            int sessionModificationState, DisconnectCause disconnectCause, String label,
            boolean isGatewayCall, boolean isWaitingForRemoteSide) {
        final Context context = getView().getContext();
        CharSequence callStateLabel = null;  // Label to display as part of the call banner

        boolean isSpecialCall = label != null;
        boolean isAccount = isSpecialCall && !isGatewayCall;

        switch  (state) {
            case Call.State.IDLE:
                // "Call state" is meaningless in this state.
                break;
            case Call.State.ACTIVE:
                // We normally don't show a "call state label" at all in this state
                // (but we can use the call state label to display the provider name).
                if (sessionModificationState
                        == Call.SessionModificationState.REQUEST_FAILED) {
                    callStateLabel = context.getString(R.string.card_title_video_call_error);
                } else if (sessionModificationState
                        == Call.SessionModificationState.WAITING_FOR_RESPONSE) {
                    callStateLabel = context.getString(R.string.card_title_video_call_requesting);
                } else if (VideoProfile.VideoState.isVideo(videoState) &&
                        VideoProfile.VideoState.isPaused(videoState)) {
                    callStateLabel = context.getString(R.string.card_title_video_call_paused);
                } else if (isWaitingForRemoteSide) {
                    callStateLabel = context.getString(R.string.accessibility_call_put_on_hold);
                } else if (VideoProfile.VideoState.isBidirectional(videoState)) {
                    callStateLabel = context.getString(R.string.card_title_video_call);
                }

                if (isAccount) {
                   label += (callStateLabel != null) ? (" " + callStateLabel) : "";
                   callStateLabel = label;
                }
                break;
            case Call.State.ONHOLD:
                callStateLabel = context.getString(R.string.card_title_on_hold);
                break;
            case Call.State.CONNECTING:
            case Call.State.DIALING:
                if (isSpecialCall) {
                    callStateLabel = context.getString(R.string.calling_via_template, label);
                } else if (isWaitingForRemoteSide) {
                    callStateLabel = context.getString(R.string.card_title_dialing_waiting);
                } else {
                    callStateLabel = context.getString(R.string.card_title_dialing);
                }
                break;
            case Call.State.REDIALING:
                callStateLabel = context.getString(R.string.card_title_redialing);
                break;
            case Call.State.INCOMING:
            case Call.State.CALL_WAITING:
                if (isAccount) {
                    callStateLabel = context.getString(R.string.incoming_via_template, label);
                } else if (VideoProfile.VideoState.isBidirectional(videoState)) {
                    callStateLabel = context.getString(R.string.notification_incoming_video_call);
                } else {
                    callStateLabel = context.getString(R.string.card_title_incoming_call);
                }
                break;
            case Call.State.DISCONNECTING:
                // While in the DISCONNECTING state we display a "Hanging up"
                // message in order to make the UI feel more responsive.  (In
                // GSM it's normal to see a delay of a couple of seconds while
                // negotiating the disconnect with the network, so the "Hanging
                // up" state at least lets the user know that we're doing
                // something.  This state is currently not used with CDMA.)
                callStateLabel = context.getString(R.string.card_title_hanging_up);
                break;
            case Call.State.DISCONNECTED:
                callStateLabel = disconnectCause.getLabel();
                if (TextUtils.isEmpty(callStateLabel)) {
                    callStateLabel = context.getString(R.string.card_title_call_ended);
                }
                if (context.getResources().getBoolean(R.bool.def_incallui_clearcode_enabled)) {
                    String clearText = disconnectCause.getDescription() == null ? "" : disconnectCause.getDescription().toString();
                    if (!TextUtils.isEmpty(clearText)) {
                        Toast.makeText(context, clearText, Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            case Call.State.CONFERENCED:
                callStateLabel = context.getString(R.string.card_title_conf_call);
                break;
            default:
                Log.wtf(this, "updateCallStateWidgets: unexpected call: " + state);
        }
        return callStateLabel;
    }

    private void showAndInitializeSecondaryCallInfo(boolean hasProvider) {
        mSecondaryCallInfo.setVisibility(View.VISIBLE);

        // mSecondaryCallName is initialized here (vs. onViewCreated) because it is inaccessible
        // until mSecondaryCallInfo is inflated in the call above.
        if (mSecondaryCallName == null) {
            mSecondaryCallName = (TextView) getView().findViewById(R.id.secondaryCallName);
            mSecondaryCallConferenceCallIcon =
                    getView().findViewById(R.id.secondaryCallConferenceCallIcon);
            if (hasProvider) {
                mSecondaryCallProviderInfo.setVisibility(View.VISIBLE);
                mSecondaryCallProviderLabel = (TextView) getView()
                        .findViewById(R.id.secondaryCallProviderLabel);
                mSecondaryCallProviderIcon = (ImageView) getView()
                        .findViewById(R.id.secondaryCallProviderIcon);
            }
        }
    }

    public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            dispatchPopulateAccessibilityEvent(event, mCallStateLabel);
            dispatchPopulateAccessibilityEvent(event, mPrimaryName);
            dispatchPopulateAccessibilityEvent(event, mPhoneNumber);
            return;
        }
        dispatchPopulateAccessibilityEvent(event, mCallStateLabel);
        dispatchPopulateAccessibilityEvent(event, mPrimaryName);
        dispatchPopulateAccessibilityEvent(event, mPhoneNumber);
        dispatchPopulateAccessibilityEvent(event, mCallTypeLabel);
        dispatchPopulateAccessibilityEvent(event, mSecondaryCallName);
        dispatchPopulateAccessibilityEvent(event, mSecondaryCallProviderLabel);

        return;
    }

    @Override
    public void setEndCallButtonEnabled(boolean enabled, boolean animate) {
        if (enabled != mFloatingActionButton.isEnabled()) {
            if (animate) {
                if (enabled) {
                    mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
                } else {
                    mFloatingActionButtonController.scaleOut();
                }
            } else {
                if (enabled) {
                    mFloatingActionButtonContainer.setScaleX(1);
                    mFloatingActionButtonContainer.setScaleY(1);
                    mFloatingActionButtonContainer.setVisibility(View.VISIBLE);
                } else {
                    mFloatingActionButtonContainer.setVisibility(View.GONE);
                }
            }
            mFloatingActionButton.setEnabled(enabled);
            updateFabPosition();
        }
    }

    /**
     * Changes the visibility of the contact photo.
     *
     * @param isVisible {@code True} if the UI should show the contact photo.
     */
    @Override
    public void setPhotoVisible(boolean isVisible) {
        mPhoto.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    /**
     * Changes the visibility of the "manage conference call" button.
     *
     * @param visible Whether to set the button to be visible or not.
     */
    @Override
    public void showManageConferenceCallButton(boolean visible) {
        mManageConferenceCallButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Determines the current visibility of the manage conference button.
     *
     * @return {@code true} if the button is visible.
     */
    @Override
    public boolean isManageConferenceVisible() {
        return mManageConferenceCallButton.getVisibility() == View.VISIBLE;
    }

    private void dispatchPopulateAccessibilityEvent(AccessibilityEvent event, View view) {
        if (view == null) return;
        final List<CharSequence> eventText = event.getText();
        int size = eventText.size();
        view.dispatchPopulateAccessibilityEvent(event);
        // if no text added write null to keep relative position
        if (size == eventText.size()) {
            eventText.add(null);
        }
    }

    public void animateForNewOutgoingCall(Point touchPoint) {
        final ViewGroup parent = (ViewGroup) mPrimaryCallCardContainer.getParent();
        final Point startPoint = touchPoint;

        final ViewTreeObserver observer = getView().getViewTreeObserver();

        mPrimaryCallInfo.getLayoutTransition().disableTransitionType(LayoutTransition.CHANGING);

        observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                final ViewTreeObserver observer = getView().getViewTreeObserver();
                if (!observer.isAlive()) {
                    return;
                }
                observer.removeOnGlobalLayoutListener(this);

                final LayoutIgnoringListener listener = new LayoutIgnoringListener();
                mPrimaryCallCardContainer.addOnLayoutChangeListener(listener);

                // Prepare the state of views before the circular reveal animation
                final int originalHeight = mPrimaryCallCardContainer.getHeight();
                mPrimaryCallCardContainer.setBottom(parent.getHeight());

                // Set up FAB.
                mFloatingActionButtonContainer.setVisibility(View.GONE);
                mFloatingActionButtonController.setScreenWidth(parent.getWidth());
                mCallButtonsContainer.setAlpha(0);
                mCallStateLabel.setAlpha(0);
                mPrimaryName.setAlpha(0);
                mCallTypeLabel.setAlpha(0);
                mCallNumberAndLabel.setAlpha(0);

                final Animator revealAnimator = getRevealAnimator(startPoint);
                final Animator shrinkAnimator =
                        getShrinkAnimator(parent.getHeight(), originalHeight);

                mAnimatorSet = new AnimatorSet();
                mAnimatorSet.playSequentially(revealAnimator, shrinkAnimator);
                mAnimatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        setViewStatePostAnimation(listener);
                    }
                });
                mAnimatorSet.start();
            }
        });
    }

    public void onDialpadVisiblityChange(boolean isShown) {
        mIsDialpadShowing = isShown;
        updateFabPosition();
    }

    private void updateFabPosition() {
        int offsetY = 0;
        if (!mIsDialpadShowing) {
            offsetY = mFloatingActionButtonVerticalOffset;
            if (mSecondaryCallInfo.isShown()) {
                offsetY -= mSecondaryCallInfo.getHeight();
            }
        }

        mFloatingActionButtonController.align(
                mIsLandscape ? FloatingActionButtonController.ALIGN_QUARTER_END
                        : FloatingActionButtonController.ALIGN_MIDDLE,
                0 /* offsetX */,
                offsetY,
                true);

        mFloatingActionButtonController.resize(
                mIsDialpadShowing ? mFabSmallDiameter : mFabNormalDiameter, true);
    }

    @Override
    public void onResume() {
        super.onResume();
        // If the previous launch animation is still running, cancel it so that we don't get
        // stuck in an intermediate animation state.
        if (mAnimatorSet != null && mAnimatorSet.isRunning()) {
            mAnimatorSet.cancel();
        }

        mIsLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        final ViewGroup parent = ((ViewGroup) mPrimaryCallCardContainer.getParent());
        final ViewTreeObserver observer = parent.getViewTreeObserver();
        parent.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                ViewTreeObserver viewTreeObserver = observer;
                if (!viewTreeObserver.isAlive()) {
                    viewTreeObserver = parent.getViewTreeObserver();
                }
                viewTreeObserver.removeOnGlobalLayoutListener(this);
                mFloatingActionButtonController.setScreenWidth(parent.getWidth());
                updateFabPosition();
            }
        });
        if (mIsRcsServiceInstalled) {
            updateUnReadSmsCount();
        }
        misEhanceScreenApkInstalled = isEnhanceScreenInstalled();
    }

    /**
     * Adds a global layout listener to update the FAB's positioning on the next layout. This allows
     * us to position the FAB after the secondary call info's height has been calculated.
     */
    private void updateFabPositionForSecondaryCallInfo() {
        mSecondaryCallInfo.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        final ViewTreeObserver observer = mSecondaryCallInfo.getViewTreeObserver();
                        if (!observer.isAlive()) {
                            return;
                        }
                        observer.removeOnGlobalLayoutListener(this);

                        onDialpadVisiblityChange(mIsDialpadShowing);
                        updateVideoCallViews();
                    }
                });
    }

    private void updateVideoCallViews() {
        Log.d(this, "updateVideoCallViews");
        View previewVideoView = getView().findViewById(R.id.previewVideo);
        View zoomControlView = getView().findViewById(R.id.zoom_control);

        final float secondaryCallInfoHeight = mSecondaryCallInfo.getHeight();
        final boolean isSecondaryCallInfoShown = mSecondaryCallInfo.isShown();
        if (previewVideoView != null) {
            previewVideoView.setTranslationY(isSecondaryCallInfoShown ?
                -secondaryCallInfoHeight : DEFAULT_VIEW_OFFSET_Y);
        }
        if (zoomControlView != null) {
            zoomControlView.setTranslationY(isSecondaryCallInfoShown ?
                -secondaryCallInfoHeight : DEFAULT_VIEW_OFFSET_Y);
        }
    }

    /**
     * Animator that performs the upwards shrinking animation of the blue call card scrim.
     * At the start of the animation, each child view is moved downwards by a pre-specified amount
     * and then translated upwards together with the scrim.
     */
    private Animator getShrinkAnimator(int startHeight, int endHeight) {
        final Animator shrinkAnimator =
                ObjectAnimator.ofInt(mPrimaryCallCardContainer, "bottom", startHeight, endHeight);
        shrinkAnimator.setDuration(mShrinkAnimationDuration);
        shrinkAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                assignTranslateAnimation(mCallStateLabel, 1);
                assignTranslateAnimation(mCallStateIcon, 1);
                assignTranslateAnimation(mPrimaryName, 2);
                assignTranslateAnimation(mCallNumberAndLabel, 3);
                assignTranslateAnimation(mCallTypeLabel, 4);
                assignTranslateAnimation(mCallButtonsContainer, 5);

                mFloatingActionButton.setEnabled(true);
            }
        });
        shrinkAnimator.setInterpolator(AnimUtils.EASE_IN);
        return shrinkAnimator;
    }

    private Animator getRevealAnimator(Point touchPoint) {
        final Activity activity = getActivity();
        final View view  = activity.getWindow().getDecorView();
        final Display display = activity.getWindowManager().getDefaultDisplay();
        final Point size = new Point();
        display.getSize(size);

        int startX = size.x / 2;
        int startY = size.y / 2;
        if (touchPoint != null) {
            startX = touchPoint.x;
            startY = touchPoint.y;
        }

        final Animator valueAnimator = ViewAnimationUtils.createCircularReveal(view,
                startX, startY, 0, Math.max(size.x, size.y));
        valueAnimator.setDuration(mRevealAnimationDuration);
        return valueAnimator;
    }

    private void assignTranslateAnimation(View view, int offset) {
        view.setTranslationY(mTranslationOffset * offset);
        view.animate().translationY(0).alpha(1).withLayer()
                .setDuration(mShrinkAnimationDuration).setInterpolator(AnimUtils.EASE_IN);
    }

    private void setViewStatePostAnimation(View view) {
        view.setTranslationY(0);
        view.setAlpha(1);
    }

    private void setViewStatePostAnimation(OnLayoutChangeListener layoutChangeListener) {
        setViewStatePostAnimation(mCallButtonsContainer);
        setViewStatePostAnimation(mCallStateLabel);
        setViewStatePostAnimation(mPrimaryName);
        setViewStatePostAnimation(mCallTypeLabel);
        setViewStatePostAnimation(mCallNumberAndLabel);
        setViewStatePostAnimation(mCallStateIcon);

        mPrimaryCallCardContainer.removeOnLayoutChangeListener(layoutChangeListener);
        mPrimaryCallInfo.getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);
        mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
    }

    private final class LayoutIgnoringListener implements View.OnLayoutChangeListener {
        @Override
        public void onLayoutChange(View v,
                int left,
                int top,
                int right,
                int bottom,
                int oldLeft,
                int oldTop,
                int oldRight,
                int oldBottom) {
            v.setLeft(oldLeft);
            v.setRight(oldRight);
            v.setTop(oldTop);
            v.setBottom(oldBottom);
        }
    }

    private OnClickListener mVBListener = new OnClickListener() {
        @Override
        public void onClick(View arg0) {
            if (isVBAvailable()) {
                switchVBStatus();
            }

            updateVBButton();
            showVBNotify();
        }
    };

    private OnClickListener mSwitchCameraListener = new OnClickListener() {
        @Override
        public void onClick(View arg0) {
            boolean useFont = mSwitchCamera.isSelected();
            getPresenter().handleSwitchCameraClicked(useFont);
            mSwitchCamera.setSelected(!useFont);
        }
    };


    private boolean isVBAvailable() {
        int mode = AudioModeProvider.getInstance().getAudioMode();
        final Activity activity = getActivity();

        int settingsTtyMode;

        if (activity != null) {
            settingsTtyMode = Settings.Secure.getInt(activity.getContentResolver(),
                    Settings.Secure.PREFERRED_TTY_MODE, TTY_MODE_OFF);
        } else {
            settingsTtyMode = TTY_MODE_OFF;
        }

        return (mode == AudioState.ROUTE_EARPIECE || mode == AudioState.ROUTE_SPEAKER
                || settingsTtyMode == TTY_MODE_HCO);
    }

    private void switchVBStatus() {
        if (mAudioManager.getParameters(VOLUME_BOOST).contains("=on")) {
            mAudioManager.setParameters(VOLUME_BOOST + "=off");
        } else {
            mAudioManager.setParameters(VOLUME_BOOST + "=on");
        }
    }

    private void updateVBButton() {
        if (isVBAvailable()
                && mAudioManager.getParameters(VOLUME_BOOST).contains("=on")) {

                mVBButton.setBackgroundResource(R.drawable.vb_active);
        } else if (isVBAvailable()
                && !(mAudioManager.getParameters(VOLUME_BOOST).contains("=on"))) {

                mVBButton.setBackgroundResource(R.drawable.vb_normal);
        } else {
            mVBButton.setBackgroundResource(R.drawable.vb_disable);
        }
    }

    private void showVBNotify() {
        if (mVBNotify != null) {
            mVBNotify.cancel();
        }

        if (isVBAvailable()
                && mAudioManager.getParameters(VOLUME_BOOST).contains("=on")) {

            mVBNotify = Toast.makeText(getView().getContext(),
                    R.string.volume_boost_notify_enabled, Toast.LENGTH_SHORT);
        } else if (isVBAvailable()
                && !(mAudioManager.getParameters(VOLUME_BOOST).contains("=on"))) {

            mVBNotify = Toast.makeText(getView().getContext(),
                    R.string.volume_boost_notify_disabled, Toast.LENGTH_SHORT);
        } else {
            mVBNotify = Toast.makeText(getView().getContext(),
                    R.string.volume_boost_notify_unavailable, Toast.LENGTH_SHORT);
        }

        mVBNotify.setGravity(Gravity.TOP, 0, mVBToastPosition);
        mVBNotify.show();
    }

    private void updateMoreMenuByCall(int state) {
        if (mMoreMenuButton == null) {
            return;
        }

        final Menu menu = mMoreMenu.getMenu();
        final MenuItem startRecord = menu.findItem(R.id.menu_start_record);
        final MenuItem stopRecord = menu.findItem(R.id.menu_stop_record);
        final MenuItem addToBlacklist = menu.findItem(R.id.menu_add_to_blacklist);

        boolean isRecording = ((InCallActivity)getActivity()).isCallRecording();
        boolean isRecordEnabled = ((InCallActivity)getActivity()).isCallRecorderEnabled();

        boolean startEnabled = !isRecording && isRecordEnabled && state == Call.State.ACTIVE;
        boolean stopEnabled = isRecording && isRecordEnabled && state == Call.State.ACTIVE;

        boolean blacklistVisible = BlacklistUtils.isBlacklistEnabled(getActivity())
                && Call.State.isConnectingOrConnected(state);

        startRecord.setVisible(startEnabled);
        startRecord.setEnabled(startEnabled);

        stopRecord.setVisible(stopEnabled);
        stopRecord.setEnabled(stopEnabled);

        addToBlacklist.setVisible(blacklistVisible);
        addToBlacklist.setEnabled(blacklistVisible);

        if (mMoreMenu.getMenu().hasVisibleItems()) {
            mMoreMenuButton.setVisibility(View.VISIBLE);
        } else {
            mMoreMenuButton.setVisibility(View.GONE);
        }
    }

    private void updateVBbyCall(int state, int videoState) {
        updateVBButton();
        if (VideoProfile.VideoState.isVideo(videoState)){
            mVBButton.setVisibility(View.INVISIBLE);
            if (mAudioManager.getParameters(VOLUME_BOOST).contains("=on")){
                mAudioManager.setParameters(VOLUME_BOOST + "=off");
            }
            return;
        }
        if (Call.State.ACTIVE == state) {
            mVBButton.setVisibility(View.VISIBLE);
        } else if (Call.State.DISCONNECTED == state || Call.State.IDLE == state) {
            if (!CallList.getInstance().hasAnyLiveCall()
                    && mAudioManager.getParameters(VOLUME_BOOST).contains("=on")) {
                mVBButton.setVisibility(View.INVISIBLE);

                mAudioManager.setParameters(VOLUME_BOOST + "=off");
            }
        }
    }

    private void updateSwitchCameraByCall(int state, int videoState){
        if ((Call.State.ACTIVE == state)&&(VideoProfile.VideoState.isVideo(videoState))){
            mSwitchCamera.setVisibility(View.VISIBLE);
        }else{
            mSwitchCamera.setVisibility(View.INVISIBLE);
        }
    }

    public void updateVBbyAudioMode(int newMode) {
        if (!(newMode == AudioState.ROUTE_EARPIECE
                || newMode == AudioState.ROUTE_BLUETOOTH
                || newMode == AudioState.ROUTE_WIRED_HEADSET
                || newMode == AudioState.ROUTE_SPEAKER)) {
            return;
        }

        if (mAudioManager != null && mAudioManager.getParameters(VOLUME_BOOST).contains("=on")) {
            mAudioManager.setParameters(VOLUME_BOOST + "=off");
        }

        updateVBButton();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unregisterReceiver(recorderStateReceiver);
    }

    private void showCallRecordingElapsedTime() {
        if (mRecordingTimeLabel.getVisibility() != View.VISIBLE) {
            AnimUtils.fadeIn(mRecordingTimeLabel, AnimUtils.DEFAULT_DURATION);
        }

        mRecordingTimeLabel.setText(mRecordingTime);
    }

    private BroadcastReceiver recorderStateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!RECORD_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }

            if (mInCallActivity.isCallRecording()) {
                recorderHandler.sendEmptyMessage(MESSAGE_TIMER);
            } else {
                mRecordingTimeLabel.setVisibility(View.GONE);
                mRecordingIcon.setVisibility(View.GONE);
            }
        }
    };

    private Handler recorderHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
            case MESSAGE_TIMER:
                if (!mInCallActivity.isCallRecording()) {
                    break;
                }

                String recordingTime = mInCallActivity.getCallRecordingTime();

                if (!TextUtils.isEmpty(recordingTime)) {
                    mRecordingTime = recordingTime;
                    mRecordingTimeLabel.setVisibility(View.VISIBLE);
                    showCallRecordingElapsedTime();
                    mRecordingIcon.setVisibility(View.VISIBLE);
                }

                if (!recorderHandler.hasMessages(MESSAGE_TIMER)) {
                    sendEmptyMessageDelayed(MESSAGE_TIMER, 1000);
                }

                break;
            }
        }
    };

    private class ModifyCallConsentListener implements DialogInterface.OnClickListener,
            DialogInterface.OnCancelListener {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            Log.d(this, "ConsentDialog: Clicked on button with ID: " + which);
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    InCallPresenter.getInstance().acceptUpgradeRequest(VideoProfile.VideoState.BIDIRECTIONAL, getActivity());
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    InCallPresenter.getInstance().declineUpgradeRequest(getActivity());
                    break;
                default:
                    Log.d(this, "ConsentDialog: No handler for this button, ID:" + which);
            }
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            Log.d(this, "ConsentDialog: Dismissing the dialog");
            InCallPresenter.getInstance().declineUpgradeRequest(getActivity());
        }
    }

    public void showModifyCallConsentDialog() {
        String title = getResources().getString(R.string.change_video_status);
        String str = getResources().getString(R.string.change_video_message);
        str = mPrimaryName.getText() + " " + str;

        final ModifyCallConsentListener listener = new ModifyCallConsentListener();
        mModifyCallPromptDialog = new AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setMessage(str)
                .setPositiveButton(R.string.modify_call_prompt_yes,
                        listener)
                .setNegativeButton(R.string.modify_call_prompt_no,
                        listener)
                .setOnCancelListener(listener)
                .create();
        mModifyCallPromptDialog.getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        mModifyCallPromptDialog.show();
    }

    public void showDowngradeToast(){
        String str = getResources().getString(R.string.change_voice_message);
        str = mPrimaryName.getText() + " " + str;
        Toast.makeText(getActivity(), str,
                Toast.LENGTH_SHORT).show();
    }

   // RCS support start
    private boolean isRcsAvailable() {
        return RcsApiManager.isRcsServiceInstalled()
                && RcsApiManager.isRcsOnline() && misEhanceScreenApkInstalled;
    }

    private boolean isEnhanceScreenInstalled() {
        boolean installed = false;
        try {
            ApplicationInfo info = getActivity().getPackageManager().getApplicationInfo(
                ENHANCE_SCREEN_APK_NAME, PackageManager.GET_PROVIDERS);
            installed = (info != null);
        } catch (NameNotFoundException e) {
        }
        Log.i(this, "Is Enhance Screen installed ? " + installed);
        return installed;
    }

    private BroadcastReceiver mSmsReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent data) {
            updateUnReadSmsCount();
        };
    };

    private class MorePopupMenu extends PopupMenu implements PopupMenu.OnMenuItemClickListener {
        public MorePopupMenu(Context context, View anchor) {
            super(context, anchor);
            setOnMenuItemClickListener(this);
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch(item.getItemId()) {
                case R.id.menu_start_record:
                    ((InCallActivity)getActivity()).startInCallRecorder();

                    return true;

                case R.id.menu_stop_record:
                    ((InCallActivity)getActivity()).stopInCallRecorder();

                    return true;

                case R.id.menu_add_to_blacklist:
                    getPresenter().blacklistClicked(getActivity());
                    return true;
            }
            return true;
        }
    }
}
