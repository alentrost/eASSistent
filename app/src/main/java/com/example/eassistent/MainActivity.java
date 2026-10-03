package com.example.eassistent;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import com.example.eassistent.data.UrnikStorage;
import com.example.eassistent.model.ClassItem;
import com.example.eassistent.model.DaySchedule;
import com.example.eassistent.model.PeriodSchedule;
import com.example.eassistent.model.ScheduleData;
import com.example.eassistent.network.UrnikFetcher;
import com.example.eassistent.parser.UrnikParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    public enum PeriodHourState {
        CURRENTLY_ACTIVE,   // Active right now (Bold strong highlight)
        NEXT_DURING_BREAK,  // Break time, next upcoming hour (Less strong color highlight)
        PASSED,             // Finished earlier today
        FUTURE              // Later in the day
    }

    // Top Header Views
    private TextView tvSchoolTitle;
    private TextView tvClassName;
    private TextView tvWeekName;
    private TextView tvLastUpdated;
    private TextView tvEmptyState;
    private TextView btnJumpToday;

    // Action Buttons
    private ImageButton btnPrevDay;
    private ImageButton btnNextDay;
    private ImageButton btnPrevWeek;
    private ImageButton btnNextWeek;
    private ImageButton btnMenu;
    private FrameLayout fabRefreshContainer;
    private ImageButton btnRefresh;
    private ProgressBar pbLoading;

    // 1. Three-Day Focus View (Smooth horizontally scrolling full week strip showing 3 days at a time)
    private FrameLayout flThreeDayWrapper;
    private HorizontalScrollView hsvThreeDayView;
    private LinearLayout llThreeDayContentRoot;
    private LinearLayout llThreeDayHeadersStrip;
    private LinearLayout llThreeDayCardsStrip;
    private LinearLayout llGradientOverlays;

    private final LinearLayout[] headerCols = new LinearLayout[7];
    private final TextView[] headerBadges = new TextView[7];
    private final TextView[] headerNames = new TextView[7];
    private final TextView[] headerDates = new TextView[7];
    private final MaterialCardView[] cardCols = new MaterialCardView[7];
    private final LinearLayout[] columnLayouts = new LinearLayout[7];

    private int columnWidth = 0;

    // 2. Full Week Table View
    private LinearLayout layoutTableView;
    private HorizontalScrollView hsvWeekTable;
    private TableLayout tlWeekTable;

    // State & Gestures
    private ScheduleData currentSchedule;
    private int focusedDayIndex = 0;
    private boolean isTableView = false; // Primary default view is 3-Day Focus View
    private int currentWeekOffset = 0; // 0 = current week, -1 = previous, +1 = next

    private VelocityTracker velocityTracker;
    private float startTouchX;
    private float startTouchY;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final TimeZone TZ_SLOVENIA = TimeZone.getTimeZone("Europe/Ljubljana");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply dark mode preference before view inflation
        if (UrnikStorage.isDarkMode(this)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupListeners();

        // Load instantly from local storage (0ms delay)
        loadCachedSchedule();
    }

    private void initViews() {
        tvSchoolTitle = findViewById(R.id.tvSchoolTitle);
        tvClassName = findViewById(R.id.tvClassName);
        tvWeekName = findViewById(R.id.tvWeekName);
        tvLastUpdated = findViewById(R.id.tvLastUpdated);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        btnJumpToday = findViewById(R.id.btnJumpToday);

        btnPrevDay = findViewById(R.id.btnPrevDay);
        btnNextDay = findViewById(R.id.btnNextDay);
        btnPrevWeek = findViewById(R.id.btnPrevWeek);
        btnNextWeek = findViewById(R.id.btnNextWeek);
        btnMenu = findViewById(R.id.btnMenu);
        fabRefreshContainer = findViewById(R.id.fabRefreshContainer);
        btnRefresh = findViewById(R.id.btnRefresh);
        pbLoading = findViewById(R.id.pbLoading);

        updateRefreshButtonPosition();

        // 3-Day Focus View (7 columns: Spacer 0, Mon 1, Tue 2, Wed 3, Thu 4, Fri 5, Spacer 6)
        flThreeDayWrapper = findViewById(R.id.flThreeDayWrapper);
        hsvThreeDayView = findViewById(R.id.hsvThreeDayView);
        llThreeDayContentRoot = findViewById(R.id.llThreeDayContentRoot);
        llThreeDayHeadersStrip = findViewById(R.id.llThreeDayHeadersStrip);
        llThreeDayCardsStrip = findViewById(R.id.llThreeDayCardsStrip);
        llGradientOverlays = findViewById(R.id.llGradientOverlays);

        headerCols[0] = findViewById(R.id.llHeader0);
        headerCols[1] = findViewById(R.id.llHeader1);
        headerCols[2] = findViewById(R.id.llHeader2);
        headerCols[3] = findViewById(R.id.llHeader3);
        headerCols[4] = findViewById(R.id.llHeader4);
        headerCols[5] = findViewById(R.id.llHeader5);
        headerCols[6] = findViewById(R.id.llHeader6);

        headerBadges[0] = findViewById(R.id.tvHeaderBadge0);
        headerBadges[1] = findViewById(R.id.tvHeaderBadge1);
        headerBadges[2] = findViewById(R.id.tvHeaderBadge2);
        headerBadges[3] = findViewById(R.id.tvHeaderBadge3);
        headerBadges[4] = findViewById(R.id.tvHeaderBadge4);
        headerBadges[5] = findViewById(R.id.tvHeaderBadge5);
        headerBadges[6] = findViewById(R.id.tvHeaderBadge6);

        headerNames[0] = findViewById(R.id.tvHeaderName0);
        headerNames[1] = findViewById(R.id.tvHeaderName1);
        headerNames[2] = findViewById(R.id.tvHeaderName2);
        headerNames[3] = findViewById(R.id.tvHeaderName3);
        headerNames[4] = findViewById(R.id.tvHeaderName4);
        headerNames[5] = findViewById(R.id.tvHeaderName5);
        headerNames[6] = findViewById(R.id.tvHeaderName6);

        headerDates[0] = findViewById(R.id.tvHeaderDate0);
        headerDates[1] = findViewById(R.id.tvHeaderDate1);
        headerDates[2] = findViewById(R.id.tvHeaderDate2);
        headerDates[3] = findViewById(R.id.tvHeaderDate3);
        headerDates[4] = findViewById(R.id.tvHeaderDate4);
        headerDates[5] = findViewById(R.id.tvHeaderDate5);
        headerDates[6] = findViewById(R.id.tvHeaderDate6);

        cardCols[0] = findViewById(R.id.cardCol0);
        cardCols[1] = findViewById(R.id.cardCol1);
        cardCols[2] = findViewById(R.id.cardCol2);
        cardCols[3] = findViewById(R.id.cardCol3);
        cardCols[4] = findViewById(R.id.cardCol4);
        cardCols[5] = findViewById(R.id.cardCol5);
        cardCols[6] = findViewById(R.id.cardCol6);

        columnLayouts[0] = findViewById(R.id.llCol0);
        columnLayouts[1] = findViewById(R.id.llCol1);
        columnLayouts[2] = findViewById(R.id.llCol2);
        columnLayouts[3] = findViewById(R.id.llCol3);
        columnLayouts[4] = findViewById(R.id.llCol4);
        columnLayouts[5] = findViewById(R.id.llCol5);
        columnLayouts[6] = findViewById(R.id.llCol6);

        for (int d = 0; d < 5; d++) {
            final int dayIdx = d;
            if (headerCols[d + 1] != null) {
                headerCols[d + 1].setOnClickListener(v -> scrollToDay(dayIdx, true));
            }
            if (cardCols[d + 1] != null) {
                cardCols[d + 1].setOnClickListener(v -> scrollToDay(dayIdx, true));
            }
        }

        setupThreeDayScroll();

        if (llGradientOverlays != null) {
            llGradientOverlays.setOnTouchListener((v, event) -> false);
        }

        flThreeDayWrapper.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int w = flThreeDayWrapper.getWidth();
            if (w > 0 && w / 3 != columnWidth) {
                updateColumnWidths(w);
                scrollToDay(focusedDayIndex, false);
            }
        });

        // Week Table View
        layoutTableView = findViewById(R.id.layoutTableView);
        hsvWeekTable = findViewById(R.id.hsvWeekTable);
        tlWeekTable = findViewById(R.id.tlWeekTable);

        updateViewVisibility();
    }

    private void setupThreeDayScroll() {
        if (hsvThreeDayView == null) return;

        hsvThreeDayView.setOnTouchListener((v, event) -> {
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
            velocityTracker.addMovement(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startTouchX = event.getRawX();
                    startTouchY = event.getRawY();
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    velocityTracker.computeCurrentVelocity(1000);
                    float vx = velocityTracker.getXVelocity();
                    float dx = event.getRawX() - startTouchX;
                    velocityTracker.recycle();
                    velocityTracker = null;

                    if (columnWidth <= 0) break;

                    int currentScrollX = hsvThreeDayView.getScrollX();
                    int targetDay;

                    int minFlingVelocity = dpToPx(350);
                    int flickDist = dpToPx(24);

                    if (Math.abs(dx) > flickDist && vx < -minFlingVelocity) {
                        targetDay = focusedDayIndex + 1;
                    } else if (Math.abs(dx) > flickDist && vx > minFlingVelocity) {
                        targetDay = focusedDayIndex - 1;
                    } else {
                        targetDay = Math.round((float) currentScrollX / columnWidth);
                    }

                    if (targetDay < 0) {
                        int curWeek = currentSchedule != null && currentSchedule.getCurrentWeekNumber() > 0
                                ? currentSchedule.getCurrentWeekNumber() : 5;
                        if (curWeek > 1) {
                            navigateWeek(-1, 4);
                        } else {
                            scrollToDay(0, true);
                        }
                    } else if (targetDay > 4) {
                        int curWeek = currentSchedule != null && currentSchedule.getCurrentWeekNumber() > 0
                                ? currentSchedule.getCurrentWeekNumber() : 5;
                        int total = currentSchedule != null && currentSchedule.getTotalWeeks() > 0
                                ? currentSchedule.getTotalWeeks() : 53;
                        if (curWeek < total) {
                            navigateWeek(1, 0);
                        } else {
                            scrollToDay(4, true);
                        }
                    } else {
                        scrollToDay(targetDay, true);
                    }
                    return true;
            }
            return false;
        });

        hsvThreeDayView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (columnWidth > 0) {
                int approxDay = Math.max(0, Math.min(4, Math.round((float) scrollX / columnWidth)));
                if (approxDay != focusedDayIndex) {
                    focusedDayIndex = approxDay;
                    updateDaySelectionVisuals(focusedDayIndex);
                }
            }
        });
    }

    private void updateColumnWidths(int containerWidth) {
        if (containerWidth <= 0) return;
        columnWidth = containerWidth / 3;
        int dayMargin = dpToPx(3.5f); // 3.5dp margin on left and right = 7dp gap between days
        int cardWidth = columnWidth - (2 * dayMargin);

        for (int i = 0; i < 7; i++) {
            LinearLayout hCol = headerCols[i];
            if (hCol != null) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) hCol.getLayoutParams();
                if (lp == null) {
                    lp = new LinearLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
                } else {
                    lp.width = cardWidth;
                }
                lp.leftMargin = dayMargin;
                lp.rightMargin = dayMargin;
                lp.setMarginStart(dayMargin);
                lp.setMarginEnd(dayMargin);
                hCol.setLayoutParams(lp);
            }

            MaterialCardView cCol = cardCols[i];
            if (cCol != null) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) cCol.getLayoutParams();
                if (lp == null) {
                    lp = new LinearLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.MATCH_PARENT);
                } else {
                    lp.width = cardWidth;
                }
                lp.leftMargin = dayMargin;
                lp.rightMargin = dayMargin;
                lp.setMarginStart(dayMargin);
                lp.setMarginEnd(dayMargin);
                cCol.setLayoutParams(lp);
            }
        }
    }

    private void scrollToDay(int dayIdx, boolean smooth) {
        if (dayIdx < 0) dayIdx = 0;
        if (dayIdx > 4) dayIdx = 4;
        this.focusedDayIndex = dayIdx;

        if (columnWidth <= 0 && flThreeDayWrapper != null) {
            updateColumnWidths(flThreeDayWrapper.getWidth());
        }

        if (columnWidth > 0 && hsvThreeDayView != null) {
            int targetX = dayIdx * columnWidth;
            if (smooth) {
                hsvThreeDayView.smoothScrollTo(targetX, 0);
            } else {
                hsvThreeDayView.scrollTo(targetX, 0);
            }
        }

        updateDaySelectionVisuals(dayIdx);
    }

    private void updateDaySelectionVisuals(int focusedIdx) {
        if (currentSchedule == null || currentSchedule.getDays().isEmpty()) return;

        for (int i = 0; i < 7; i++) {
            LinearLayout hCol = headerCols[i];
            MaterialCardView cCol = cardCols[i];
            if (hCol == null || cCol == null) continue;

            if (i == 0) {
                float alpha = (focusedIdx == 0) ? 0.78f : 0.45f;
                hCol.setAlpha(alpha);
                cCol.setAlpha(alpha);
                hCol.setBackgroundResource(R.drawable.bg_header_dimmed);
                cCol.setStrokeColor(ContextCompat.getColor(this, R.color.ea_card_border));
                cCol.setStrokeWidth(dpToPx(1f));
                continue;
            }

            if (i == 6) {
                float alpha = (focusedIdx == 4) ? 0.78f : 0.45f;
                hCol.setAlpha(alpha);
                cCol.setAlpha(alpha);
                hCol.setBackgroundResource(R.drawable.bg_header_dimmed);
                cCol.setStrokeColor(ContextCompat.getColor(this, R.color.ea_card_border));
                cCol.setStrokeWidth(dpToPx(1f));
                continue;
            }

            int dayIdx = i - 1; // 0..4
            boolean isFocused = (dayIdx == focusedIdx);
            boolean isAdjacent = (Math.abs(dayIdx - focusedIdx) == 1);
            boolean isToday = (dayIdx < currentSchedule.getDays().size() && isDayActuallyToday(currentSchedule.getDays().get(dayIdx)));

            if (isFocused) {
                hCol.setAlpha(1.0f);
                cCol.setAlpha(1.0f);
                if (isToday) {
                    hCol.setBackgroundResource(R.drawable.bg_header_today);
                    cCol.setStrokeColor(ContextCompat.getColor(this, R.color.ea_today_border));
                    cCol.setStrokeWidth(dpToPx(1.5f));
                } else {
                    hCol.setBackgroundResource(R.drawable.bg_header_dimmed);
                    cCol.setStrokeColor(ContextCompat.getColor(this, R.color.ea_card_border));
                    cCol.setStrokeWidth(dpToPx(1f));
                }
            } else if (isAdjacent) {
                hCol.setAlpha(0.78f);
                cCol.setAlpha(0.78f);
                if (isToday) {
                    hCol.setBackgroundResource(R.drawable.bg_header_today);
                    cCol.setStrokeColor(ContextCompat.getColor(this, R.color.ea_today_border));
                    cCol.setStrokeWidth(dpToPx(1.5f));
                } else {
                    hCol.setBackgroundResource(R.drawable.bg_header_dimmed);
                    cCol.setStrokeColor(ContextCompat.getColor(this, R.color.ea_card_border));
                    cCol.setStrokeWidth(dpToPx(1f));
                }
            } else {
                hCol.setAlpha(0.45f);
                cCol.setAlpha(0.45f);
                hCol.setBackgroundResource(R.drawable.bg_header_dimmed);
                cCol.setStrokeColor(ContextCompat.getColor(this, R.color.ea_card_border));
                cCol.setStrokeWidth(dpToPx(1f));
            }
        }
    }

    private void navigateDay(int delta) {
        if (currentSchedule != null && !currentSchedule.getDays().isEmpty()) {
            int newIndex = focusedDayIndex + delta;
            if (newIndex >= 0 && newIndex < currentSchedule.getDays().size()) {
                scrollToDay(newIndex, true);
            } else if (newIndex < 0) {
                int curWeek = currentSchedule.getCurrentWeekNumber() > 0 ? currentSchedule.getCurrentWeekNumber() : 5;
                if (curWeek > 1) {
                    navigateWeek(-1, currentSchedule.getDays().size() - 1);
                }
            } else {
                int curWeek = currentSchedule.getCurrentWeekNumber() > 0 ? currentSchedule.getCurrentWeekNumber() : 5;
                int total = currentSchedule.getTotalWeeks() > 0 ? currentSchedule.getTotalWeeks() : 53;
                if (curWeek < total) {
                    navigateWeek(1, 0);
                }
            }
        }
    }

    private void setupListeners() {
        btnRefresh.setOnClickListener(v -> refreshScheduleFromNetwork());

        btnMenu.setOnClickListener(this::showPopupMenu);

        btnPrevDay.setOnClickListener(v -> navigateDay(-1));
        btnNextDay.setOnClickListener(v -> navigateDay(1));

        if (btnPrevWeek != null) {
            btnPrevWeek.setOnClickListener(v -> navigateWeek(-1, -1));
        }
        if (btnNextWeek != null) {
            btnNextWeek.setOnClickListener(v -> navigateWeek(1, -1));
        }
        if (tvWeekName != null) {
            tvWeekName.setOnClickListener(v -> showWeekPickerDialog());
        }

        btnJumpToday.setOnClickListener(v -> {
            if (currentSchedule != null) {
                int targetIndex = getTodayDayIndex(currentSchedule);
                if (targetIndex != focusedDayIndex) {
                    scrollToDay(targetIndex, true);
                } else {
                    refreshScheduleFromNetwork();
                }
            }
        });
    }

    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, isTableView ? "3-dnevni pogled" : "Tedenska tabela");
        popup.getMenu().add(0, 2, 1, "Nastavitve");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                toggleViewMode();
                return true;
            } else if (item.getItemId() == 2) {
                showSettingsDialog();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private static class WeekPickerItem {
        final int weekNumber;
        final String title;
        final String dateRange;
        final boolean isCurrentByDate;

        WeekPickerItem(int weekNumber, String title, String dateRange, boolean isCurrentByDate) {
            this.weekNumber = weekNumber;
            this.title = title;
            this.dateRange = dateRange;
            this.isCurrentByDate = isCurrentByDate;
        }
    }

    private int getCurrentWeekByDate(ScheduleData schedule) {
        if (schedule == null) return 5;
        int curWeek = schedule.getCurrentWeekNumber() > 0 ? schedule.getCurrentWeekNumber() : 5;

        Calendar baseMonday = Calendar.getInstance(TZ_SLOVENIA);
        boolean dateFound = false;

        if (!schedule.getDays().isEmpty()) {
            String dateText = schedule.getDays().get(0).getDateText();
            if (dateText != null && !dateText.isEmpty()) {
                String[] parts = dateText.replace(".", " ").trim().split("\\s+");
                if (parts.length >= 2) {
                    try {
                        int day = Integer.parseInt(parts[0]);
                        int month = Integer.parseInt(parts[1]) - 1;
                        int year = parts.length >= 3 ? Integer.parseInt(parts[2]) : baseMonday.get(Calendar.YEAR);
                        baseMonday.set(Calendar.YEAR, year);
                        baseMonday.set(Calendar.MONTH, month);
                        baseMonday.set(Calendar.DAY_OF_MONTH, day);
                        baseMonday.set(Calendar.HOUR_OF_DAY, 12);
                        baseMonday.set(Calendar.MINUTE, 0);
                        baseMonday.set(Calendar.SECOND, 0);
                        baseMonday.set(Calendar.MILLISECOND, 0);
                        dateFound = true;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        if (!dateFound) {
            return curWeek;
        }

        Calendar now = Calendar.getInstance(TZ_SLOVENIA);
        now.set(Calendar.HOUR_OF_DAY, 12);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);

        long diffMillis = now.getTimeInMillis() - baseMonday.getTimeInMillis();
        double diffDays = (double) diffMillis / (1000.0 * 60 * 60 * 24);
        int weekOffset = (int) Math.floor(diffDays / 7.0);

        int result = curWeek + weekOffset;
        if (result < 1) result = 1;
        int total = schedule.getTotalWeeks() > 0 ? schedule.getTotalWeeks() : 53;
        if (result > total) result = total;
        return result;
    }

    private String getWeekDateRange(int targetWeek, ScheduleData schedule) {
        if (schedule == null) return "";

        int curWeek = schedule.getCurrentWeekNumber();
        if (curWeek <= 0) curWeek = 5;

        Calendar baseMonday = Calendar.getInstance(TZ_SLOVENIA);
        boolean dateFound = false;

        if (!schedule.getDays().isEmpty()) {
            String dateText = schedule.getDays().get(0).getDateText();
            if (dateText != null && !dateText.isEmpty()) {
                String[] parts = dateText.replace(".", " ").trim().split("\\s+");
                if (parts.length >= 2) {
                    try {
                        int day = Integer.parseInt(parts[0]);
                        int month = Integer.parseInt(parts[1]) - 1;
                        int year = parts.length >= 3 ? Integer.parseInt(parts[2]) : baseMonday.get(Calendar.YEAR);
                        baseMonday.set(Calendar.YEAR, year);
                        baseMonday.set(Calendar.MONTH, month);
                        baseMonday.set(Calendar.DAY_OF_MONTH, day);
                        dateFound = true;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        if (!dateFound) {
            int dayOfWeek = baseMonday.get(Calendar.DAY_OF_WEEK);
            int daysFromMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : (dayOfWeek - Calendar.MONDAY);
            baseMonday.add(Calendar.DAY_OF_YEAR, -daysFromMonday);
        }

        Calendar targetCal = (Calendar) baseMonday.clone();
        targetCal.add(Calendar.DAY_OF_YEAR, (targetWeek - curWeek) * 7);

        int startDay = targetCal.get(Calendar.DAY_OF_MONTH);
        int startMonth = targetCal.get(Calendar.MONTH) + 1;

        targetCal.add(Calendar.DAY_OF_YEAR, 6);
        int endDay = targetCal.get(Calendar.DAY_OF_MONTH);
        int endMonth = targetCal.get(Calendar.MONTH) + 1;

        return startDay + ". " + startMonth + ". – " + endDay + ". " + endMonth + ".";
    }

    private void showWeekPickerDialog() {
        if (currentSchedule == null) return;
        int total = currentSchedule.getTotalWeeks() > 0 ? currentSchedule.getTotalWeeks() : 53;
        int cur = currentSchedule.getCurrentWeekNumber() > 0 ? currentSchedule.getCurrentWeekNumber() : 5;
        int todayWeek = getCurrentWeekByDate(currentSchedule);

        Calendar todayCal = Calendar.getInstance(TZ_SLOVENIA);
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE, d. M.", new Locale("sl", "SI"));
        String todayDateStr = dayFormat.format(todayCal.getTime());

        List<WeekPickerItem> items = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < total; i++) {
            int w = i + 1;
            boolean isTodayWeek = (w == todayWeek);
            String title = "Teden " + w;
            String dateRange = getWeekDateRange(w, currentSchedule);
            items.add(new WeekPickerItem(w, title, dateRange, isTodayWeek));
            if (w == cur) selectedIndex = i;
        }

        final int currentSelectedIndex = selectedIndex;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_week_picker, null);
        ListView lvWeeks = dialogView.findViewById(R.id.lvWeeks);
        ImageButton btnClose = dialogView.findViewById(R.id.btnCloseWeekPicker);
        TextView tvCurrentWeekByDate = dialogView.findViewById(R.id.tvCurrentWeekByDate);

        if (tvCurrentWeekByDate != null) {
            tvCurrentWeekByDate.setText("Danes po datumu: Teden " + todayWeek + " (" + todayDateStr + ")");
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return items.size();
            }

            @Override
            public WeekPickerItem getItem(int position) {
                return items.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.item_dialog_week_picker, parent, false);
                }
                WeekPickerItem item = getItem(position);
                LinearLayout llRoot = convertView.findViewById(R.id.llWeekPickerItemRoot);
                TextView tvTitle = convertView.findViewById(R.id.tvWeekTitle);
                TextView tvBadge = convertView.findViewById(R.id.tvCurrentBadge);
                TextView tvDateRange = convertView.findViewById(R.id.tvWeekDateRange);
                ImageView ivCheck = convertView.findViewById(R.id.ivCheck);

                boolean isSelected = (position == currentSelectedIndex);

                if (isSelected) {
                    llRoot.setBackgroundResource(R.drawable.bg_week_picker_selected);
                    tvTitle.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.ea_blue));
                    tvDateRange.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.ea_blue));
                    ivCheck.setVisibility(View.VISIBLE);
                } else {
                    llRoot.setBackgroundResource(R.drawable.bg_week_picker_unselected);
                    tvTitle.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.ea_text_primary));
                    tvDateRange.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.ea_text_secondary));
                    ivCheck.setVisibility(View.GONE);
                }

                tvTitle.setText(item.title);
                tvDateRange.setText(item.dateRange);
                tvBadge.setVisibility(item.isCurrentByDate ? View.VISIBLE : View.GONE);
                tvBadge.setText("danes");

                return convertView;
            }
        };

        lvWeeks.setAdapter(adapter);
        lvWeeks.setSelection(Math.max(0, currentSelectedIndex - 2));

        lvWeeks.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            WeekPickerItem item = items.get(position);
            int targetWeek = item.weekNumber;
            if (targetWeek != cur) {
                fetchWeek(targetWeek, -1);
            }
        });

        dialog.show();
    }

    private void navigateWeek(int delta, int targetDayIndex) {
        if (currentSchedule == null) return;
        int currentWeek = currentSchedule.getCurrentWeekNumber();
        if (currentWeek <= 0) currentWeek = 5; // Fallback default
        int targetWeek = currentWeek + delta;
        if (targetWeek < 1) return;
        if (currentSchedule.getTotalWeeks() > 0 && targetWeek > currentSchedule.getTotalWeeks()) return;
        
        fetchWeek(targetWeek, targetDayIndex);
    }
    
    private void fetchWeek(int weekNumber, int targetDayIndex) {
        if (currentSchedule == null) return;
        final String baseUrl = UrnikStorage.getSavedUrl(this);
        int idSola = currentSchedule.getIdSola();
        if (idSola == 0) {
            idSola = 224; // Default fallback for Šolski center Nova Gorica
        }
        
        final String ajaxUrl = UrnikFetcher.buildAjaxWeekUrl(baseUrl, idSola, weekNumber);
        if (ajaxUrl == null) return;
        
        pbLoading.setVisibility(View.VISIBLE);
        btnRefresh.setVisibility(View.GONE);
        
        final ScheduleData baseData = currentSchedule;
        
        executor.execute(() -> {
            try {
                String response = UrnikFetcher.fetchWeekAjaxSync(ajaxUrl);
                ScheduleData weekSchedule = UrnikParser.parseAjaxWeek(response, baseUrl, baseData);
                
                if (weekSchedule != null && !weekSchedule.getDays().isEmpty()) {
                    UrnikStorage.saveSchedule(MainActivity.this, weekSchedule);
                    
                    mainHandler.post(() -> {
                        pbLoading.setVisibility(View.GONE);
                        btnRefresh.setVisibility(View.VISIBLE);
                        displaySchedule(weekSchedule);
                        if (targetDayIndex >= 0 && targetDayIndex < weekSchedule.getDays().size()) {
                            focusedDayIndex = targetDayIndex;
                            if (!isTableView) {
                                scrollToDay(focusedDayIndex, true);
                            }
                        }
                    });
                } else {
                    throw new Exception("Podatkov o urniku ni bilo mogoče prebrati.");
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    pbLoading.setVisibility(View.GONE);
                    btnRefresh.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void toggleViewMode() {
        isTableView = !isTableView;
        updateViewVisibility();
        if (currentSchedule != null) {
            if (isTableView) {
                renderWeekTable(currentSchedule);
                scrollToCenterTodayInTable();
            } else {
                renderThreeDayView(currentSchedule, focusedDayIndex);
            }
        }
    }

    private void updateRefreshButtonPosition() {
        if (fabRefreshContainer == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fabRefreshContainer.getLayoutParams();
        boolean isLeft = UrnikStorage.isRefreshButtonLeft(this);
        lp.gravity = Gravity.BOTTOM | (isLeft ? Gravity.START : Gravity.END);
        fabRefreshContainer.setLayoutParams(lp);
    }

    private void updateViewVisibility() {
        if (flThreeDayWrapper != null) {
            flThreeDayWrapper.setVisibility(!isTableView ? View.VISIBLE : View.GONE);
        }
        if (layoutTableView != null) {
            layoutTableView.setVisibility(isTableView ? View.VISIBLE : View.GONE);
        }
    }

    private void loadCachedSchedule() {
        ScheduleData cached = UrnikStorage.loadSchedule(this);
        if (cached != null) {
            displaySchedule(cached);
            if (isWeekend()) {
                // If today is Saturday or Sunday, refresh from network to load next week's Monday schedule
                refreshScheduleFromNetwork();
            }
        } else {
            refreshScheduleFromNetwork();
        }
    }

    private void displaySchedule(ScheduleData data) {
        this.currentSchedule = data;
        if (data == null || data.getDays().isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            if (flThreeDayWrapper != null) {
                flThreeDayWrapper.setVisibility(View.GONE);
            }
            if (layoutTableView != null) {
                layoutTableView.setVisibility(View.GONE);
            }
            return;
        }

        tvEmptyState.setVisibility(View.GONE);
        updateViewVisibility();

        // Header metadata
        tvClassName.setText(cleanClassName(data.getClassName()));
        tvSchoolTitle.setText(data.getSchoolTitle().isEmpty() ? "eAsistent" : data.getSchoolTitle());
        tvWeekName.setText(data.getWeekText().isEmpty() ? "Urnik" : data.getWeekText());

        if (data.getLastUpdatedMillis() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            sdf.setTimeZone(TZ_SLOVENIA);
            tvLastUpdated.setText(sdf.format(new Date(data.getLastUpdatedMillis())));
        } else {
            tvLastUpdated.setText("--:--");
        }

        focusedDayIndex = getTodayDayIndex(data);

        // Render current mode
        if (isTableView) {
            renderWeekTable(data);
            scrollToCenterTodayInTable();
        } else {
            renderThreeDayView(data, focusedDayIndex);
        }
    }

    private String cleanClassName(String raw) {
        if (raw == null) return "Urnik";
        String cleaned = raw.replaceAll("\\s*\\([^)]*\\)", "").trim();
        return cleaned.isEmpty() ? "Urnik" : cleaned;
    }

    private boolean isWeekend() {
        Calendar cal = Calendar.getInstance(TZ_SLOVENIA);
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        return (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY);
    }

    private boolean isDayActuallyToday(DaySchedule day) {
        if (day == null) return false;
        if (isWeekend()) return false;
        if (day.isToday()) return true;
        SimpleDateFormat daySdf = new SimpleDateFormat("d. M.", Locale.getDefault());
        daySdf.setTimeZone(TZ_SLOVENIA);
        String todayDateStr = daySdf.format(new Date());
        return day.getDateText() != null && day.getDateText().trim().startsWith(todayDateStr);
    }

    private int getTodayDayIndex(ScheduleData data) {
        if (data == null || data.getDays().isEmpty()) return 0;
        
        // 1. Weekend (Saturday or Sunday): open on next week Monday (index 0)
        if (isWeekend()) {
            return 0;
        }

        // 2. Check if parser marked any day as today
        for (int i = 0; i < data.getDays().size(); i++) {
            if (data.getDays().get(i).isToday()) {
                return i;
            }
        }
        
        // 3. Match by current date in Slovenia timezone (e.g. "1. 10.")
        SimpleDateFormat daySdf = new SimpleDateFormat("d. M.", Locale.getDefault());
        daySdf.setTimeZone(TZ_SLOVENIA);
        String todayDateStr = daySdf.format(new Date());
        for (int i = 0; i < data.getDays().size(); i++) {
            String dateText = data.getDays().get(i).getDateText();
            if (dateText != null && dateText.trim().startsWith(todayDateStr)) {
                return i;
            }
        }
        
        // 4. Fallback by Calendar DAY_OF_WEEK (MONDAY = 0)
        Calendar cal = Calendar.getInstance(TZ_SLOVENIA);
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int mapped = dow - Calendar.MONDAY;
        if (mapped >= 0 && mapped < data.getDays().size()) {
            return mapped;
        }
        
        return 0;
    }

    /**
     * Renders all 5 days of the week into the horizontal 7-column strip (Spacer, Mon..Fri, Spacer).
     * Viewport displays exactly 3 days at a time.
     */
    private void renderThreeDayView(ScheduleData data, int centerIdx) {
        if (data == null || data.getDays().isEmpty()) return;

        int totalDays = data.getDays().size();
        if (centerIdx < 0) centerIdx = 0;
        if (centerIdx >= totalDays) centerIdx = totalDays - 1;
        this.focusedDayIndex = centerIdx;

        int containerWidth = flThreeDayWrapper.getWidth();
        if (containerWidth > 0) {
            updateColumnWidths(containerWidth);
        } else {
            flThreeDayWrapper.post(() -> {
                updateColumnWidths(flThreeDayWrapper.getWidth());
                scrollToDay(focusedDayIndex, false);
            });
        }

        // Configure Left Spacer (Col 0)
        headerBadges[0].setVisibility(View.GONE);
        headerNames[0].setText("—");
        headerDates[0].setText("Ni podatka");
        columnLayouts[0].removeAllViews();

        // Configure Right Spacer (Col 6)
        headerBadges[6].setVisibility(View.GONE);
        headerNames[6].setText("—");
        headerDates[6].setText("Ni podatka");
        columnLayouts[6].removeAllViews();

        int[] range = getVisibleHourRange(data);
        int startHour = range[0];
        int endHour = range[1];

        LayoutInflater inflater = LayoutInflater.from(this);

        Calendar now = Calendar.getInstance(TZ_SLOVENIA);
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        // Render each day (Cols 1 to 5 for Days 0 to 4)
        for (int d = 0; d < 5; d++) {
            int colIdx = d + 1;
            columnLayouts[colIdx].removeAllViews();

            if (d < data.getDays().size()) {
                DaySchedule day = data.getDays().get(d);
                boolean isToday = isDayActuallyToday(day);

                if (isToday) {
                    headerBadges[colIdx].setVisibility(View.VISIBLE);
                    headerBadges[colIdx].setText("⭐ DANES");
                } else {
                    headerBadges[colIdx].setVisibility(View.GONE);
                    headerBadges[colIdx].setText("");
                }

                headerNames[colIdx].setText(day.getDayName());
                headerDates[colIdx].setText(day.getDateText());

                int activePeriodIdx = -1;
                int nextPeriodIdx = -1;

                if (isToday) {
                    for (int i = 0; i < day.getPeriods().size(); i++) {
                        PeriodSchedule p = day.getPeriods().get(i);
                        int[] se = parseStartEndMinutes(p);
                        int start = se[0];
                        int end = se[1];
                        if (start > 0 && end > 0) {
                            if (nowMinutes >= start && nowMinutes < end) {
                                activePeriodIdx = i;
                                break;
                            } else if (nowMinutes < start) {
                                if (nextPeriodIdx == -1) {
                                    nextPeriodIdx = i;
                                }
                            }
                        }
                    }
                }

                for (int h = startHour; h <= endHour; h++) {
                    PeriodSchedule period = h < day.getPeriods().size() ? day.getPeriods().get(h) : null;

                    if (h > startHour) {
                        addHourDivider(columnLayouts[colIdx]);
                    }

                    PeriodHourState state = PeriodHourState.FUTURE;
                    boolean isMalica = isMalicaPeriod(period);

                    if (isToday) {
                        if (h == activePeriodIdx) {
                            state = PeriodHourState.CURRENTLY_ACTIVE;
                        } else if (activePeriodIdx == -1 && h == nextPeriodIdx) {
                            state = PeriodHourState.NEXT_DURING_BREAK;
                        } else if (period != null) {
                            int[] se = parseStartEndMinutes(period);
                            if (se[1] > 0 && nowMinutes >= se[1]) {
                                state = PeriodHourState.PASSED;
                            }
                        }
                    }

                    LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);

                    View cellView = createCellView(period, isToday, state, isMalica, inflater, columnLayouts[colIdx]);
                    cellView.setLayoutParams(cellLp);
                    columnLayouts[colIdx].addView(cellView);
                }
            } else {
                headerBadges[colIdx].setVisibility(View.GONE);
                headerNames[colIdx].setText("—");
                headerDates[colIdx].setText("Ni podatka");
            }
        }

        updateDaySelectionVisuals(focusedDayIndex);

        hsvThreeDayView.post(() -> scrollToDay(focusedDayIndex, false));
    }

    private void addHourDivider(LinearLayout col) {
        View div = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1.5f));
        lp.setMargins(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2));
        div.setLayoutParams(lp);
        div.setBackgroundColor(ContextCompat.getColor(this, R.color.ea_hour_divider));
        col.addView(div);
    }

    private boolean isHourEmptyAcrossWeek(ScheduleData data, int hourIdx) {
        if (data == null || data.getDays().isEmpty()) return true;
        for (DaySchedule day : data.getDays()) {
            if (hourIdx < day.getPeriods().size()) {
                PeriodSchedule period = day.getPeriods().get(hourIdx);
                if (period != null && (!period.isEmpty() || isMalicaPeriod(period))) {
                    return false;
                }
            }
        }
        return true;
    }

    private int[] getVisibleHourRange(ScheduleData data) {
        if (data == null || data.getDays().isEmpty()) {
            return new int[]{0, 0};
        }
        int maxHours = 0;
        for (DaySchedule d : data.getDays()) {
            if (d.getPeriods().size() > maxHours) {
                maxHours = d.getPeriods().size();
            }
        }
        if (maxHours == 0) return new int[]{0, 0};

        int startHour = 0;
        while (startHour < maxHours && isHourEmptyAcrossWeek(data, startHour)) {
            startHour++;
        }

        int endHour = maxHours - 1;
        while (endHour >= startHour && isHourEmptyAcrossWeek(data, endHour)) {
            endHour--;
        }

        if (startHour > endHour) {
            startHour = 0;
            endHour = maxHours - 1;
        }

        return new int[]{startHour, endHour};
    }

    /**
     * Builds individual cell view populated with class items, malica, or empty state.
     */
    private View createCellView(PeriodSchedule period, boolean isTodayColumn, PeriodHourState state, boolean isMalica,
                                LayoutInflater inflater, ViewGroup parent) {
        View cellView = inflater.inflate(R.layout.item_three_day_cell, parent, false);
        LinearLayout llCellRoot = cellView.findViewById(R.id.llCellRoot);
        LinearLayout llCellItems = cellView.findViewById(R.id.llCellItems);
        LinearLayout llCellEmpty = cellView.findViewById(R.id.llCellEmpty);
        LinearLayout llCellMalica = cellView.findViewById(R.id.llCellMalica);

        if (isMalica) {
            llCellMalica.setVisibility(View.VISIBLE);
            llCellEmpty.setVisibility(View.GONE);
            llCellItems.setVisibility(View.GONE);

            TextView tvMalica = cellView.findViewById(R.id.tvMalicaTitle);
            if (isTodayColumn) {
                llCellRoot.setBackgroundResource(R.drawable.bg_card_malica);
                if (tvMalica != null) {
                    tvMalica.setTextColor(ContextCompat.getColor(this, R.color.ea_malica_text));
                }
            } else {
                llCellRoot.setBackgroundResource(R.drawable.bg_cell_malica_dimmed);
                if (tvMalica != null) {
                    tvMalica.setTextColor(ContextCompat.getColor(this, R.color.ea_malica_dimmed_text));
                }
            }

            return cellView;
        }

        if (period == null || period.isEmpty()) {
            llCellEmpty.setVisibility(View.VISIBLE);
            llCellMalica.setVisibility(View.GONE);
            llCellItems.setVisibility(View.GONE);

            if (isTodayColumn) {
                if (state == PeriodHourState.CURRENTLY_ACTIVE) {
                    llCellRoot.setBackgroundResource(R.drawable.bg_cell_today_active);
                } else if (state == PeriodHourState.NEXT_DURING_BREAK) {
                    llCellRoot.setBackgroundResource(R.drawable.bg_cell_today_next);
                } else {
                    llCellRoot.setBackgroundColor(Color.TRANSPARENT);
                }
            } else {
                llCellRoot.setBackgroundColor(Color.TRANSPARENT);
            }
            return cellView;
        }

        // Has class items
        llCellEmpty.setVisibility(View.GONE);
        llCellMalica.setVisibility(View.GONE);
        llCellItems.setVisibility(View.VISIBLE);

        // Apply background styling based on state
        if (isTodayColumn) {
            if (state == PeriodHourState.CURRENTLY_ACTIVE) {
                llCellRoot.setBackgroundResource(R.drawable.bg_cell_today_active);
            } else if (state == PeriodHourState.NEXT_DURING_BREAK) {
                llCellRoot.setBackgroundResource(R.drawable.bg_cell_today_next);
            } else {
                llCellRoot.setBackgroundColor(Color.TRANSPARENT);
            }
        } else {
            llCellRoot.setBackgroundColor(Color.TRANSPARENT);
        }

        List<ClassItem> items = period.getItems();
        boolean hasMultiple = items != null && items.size() > 1;
        boolean allSameSubject = false;
        if (hasMultiple) {
            String firstSubj = items.get(0).getSubject().trim();
            allSameSubject = true;
            for (ClassItem it : items) {
                if (!it.getSubject().trim().equalsIgnoreCase(firstSubj)) {
                    allSameSubject = false;
                    break;
                }
            }
        }

        if (hasMultiple && allSameSubject) {
            // Display subject name ONCE at the top, followed by dashed line, then Professor on left + Class number on right
            View sharedView = inflater.inflate(R.layout.item_three_day_shared_subject, llCellItems, false);
            TextView tvSharedSubj = sharedView.findViewById(R.id.tvSharedSubject);
            View vSubjectDivider = sharedView.findViewById(R.id.vSubjectDivider);
            if (vSubjectDivider != null) {
                vSubjectDivider.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            }
            LinearLayout llSharedGroups = sharedView.findViewById(R.id.llSharedGroups);

            String subjName = items.get(0).getSubject();
            if (isTodayColumn && state == PeriodHourState.CURRENTLY_ACTIVE) {
                subjName = "▶ " + subjName;
                tvSharedSubj.setTextColor(ContextCompat.getColor(this, R.color.ea_hour_active_text));
            } else if (isTodayColumn && state == PeriodHourState.NEXT_DURING_BREAK) {
                subjName = "⏳ " + subjName;
                tvSharedSubj.setTextColor(ContextCompat.getColor(this, R.color.ea_hour_next_text));
            } else {
                tvSharedSubj.setTextColor(ContextCompat.getColor(this, R.color.ea_text_primary));
            }
            tvSharedSubj.setText(subjName);

            for (int i = 0; i < items.size(); i++) {
                ClassItem item = items.get(i);
                View groupView = inflater.inflate(R.layout.item_three_day_group_row, llSharedGroups, false);
                TextView tvGroupTeacher = groupView.findViewById(R.id.tvGroupTeacher);
                TextView tvGroupRoom = groupView.findViewById(R.id.tvGroupRoom);

                if (!item.getProfessor().isEmpty()) {
                    tvGroupTeacher.setText(item.getProfessor());
                    tvGroupTeacher.setVisibility(View.VISIBLE);
                } else {
                    tvGroupTeacher.setVisibility(View.GONE);
                }

                if (!item.getClassroom().isEmpty()) {
                    tvGroupRoom.setText(item.getClassroom());
                    tvGroupRoom.setVisibility(View.VISIBLE);
                } else {
                    tvGroupRoom.setVisibility(View.GONE);
                }

                llSharedGroups.addView(groupView);

                if (i < items.size() - 1) {
                    View divider = new View(this);
                    divider.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1.5f));
                    lp.setMargins(0, dpToPx(1f), 0, dpToPx(1f));
                    divider.setLayoutParams(lp);
                    divider.setBackgroundResource(R.drawable.divider_class_group_dashed);
                    llSharedGroups.addView(divider);
                }
            }

            llCellItems.addView(sharedView);
        } else {
            for (int i = 0; i < period.getItems().size(); i++) {
                ClassItem item = period.getItems().get(i);
                View subView = inflater.inflate(R.layout.item_three_day_subitem, llCellItems, false);

                TextView tvSubj = subView.findViewById(R.id.tvSubitemSubject);
                TextView tvRoom = subView.findViewById(R.id.tvSubitemRoom);
                TextView tvTeacher = subView.findViewById(R.id.tvSubitemTeacher);

                String subjText = item.getSubject();
                if (isTodayColumn && state == PeriodHourState.CURRENTLY_ACTIVE) {
                    subjText = "▶ " + item.getSubject();
                    tvSubj.setTextColor(ContextCompat.getColor(this, R.color.ea_hour_active_text));
                } else if (isTodayColumn && state == PeriodHourState.NEXT_DURING_BREAK) {
                    subjText = "⏳ " + item.getSubject();
                    tvSubj.setTextColor(ContextCompat.getColor(this, R.color.ea_hour_next_text));
                } else {
                    tvSubj.setTextColor(ContextCompat.getColor(this, R.color.ea_text_primary));
                }
                tvSubj.setText(subjText);

                if (!item.getClassroom().isEmpty()) {
                    tvRoom.setText(item.getClassroom());
                    tvRoom.setVisibility(View.VISIBLE);
                } else {
                    tvRoom.setVisibility(View.GONE);
                }

                if (!item.getProfessor().isEmpty()) {
                    tvTeacher.setText(item.getProfessor());
                    tvTeacher.setVisibility(View.VISIBLE);
                } else {
                    tvTeacher.setVisibility(View.GONE);
                }

                llCellItems.addView(subView);

                // Distinct dashed divider between multiple classes occurring in the same period
                if (i < period.getItems().size() - 1) {
                    View divider = new View(this);
                    divider.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(2f));
                    lp.setMargins(dpToPx(4), dpToPx(1), dpToPx(4), dpToPx(1));
                    divider.setLayoutParams(lp);
                    divider.setBackgroundResource(R.drawable.divider_class_group_dashed);
                    llCellItems.addView(divider);
                }
            }
        }

        return cellView;
    }

    /**
     * Determines whether a period is the school lunch break ("malica").
     */
    private boolean isMalicaPeriod(PeriodSchedule period) {
        if (period == null) return false;
        if (!period.isEmpty()) return false;

        if (period.getHourNumber() == 5) return true;
        if (period.getHourName().contains("5.")) return true;
        if (period.getHourTime().contains("11:00")) return true;

        return false;
    }

    private int[] parseStartEndMinutes(PeriodSchedule period) {
        if (period != null) {
            String timeStr = period.getHourTime();
            if (timeStr != null && timeStr.contains("-")) {
                try {
                    String[] parts = timeStr.split("-");
                    int start = parseClockMinutes(parts[0].trim());
                    int end = parseClockMinutes(parts[1].trim());
                    if (start > 0 && end > 0) {
                        return new int[]{start, end};
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // Fallback timetable slots
        int h = period != null ? period.getHourNumber() : 1;
        switch (h) {
            case 0: return new int[]{6 * 60 + 55, 7 * 60 + 40};
            case 1: return new int[]{7 * 60 + 45, 8 * 60 + 30};
            case 2: return new int[]{8 * 60 + 35, 9 * 60 + 20};
            case 3: return new int[]{9 * 60 + 25, 10 * 60 + 10};
            case 4: return new int[]{10 * 60 + 15, 11 * 60};
            case 5: return new int[]{11 * 60, 11 * 60 + 45};
            case 6: return new int[]{11 * 60 + 50, 12 * 60 + 35};
            case 7: return new int[]{12 * 60 + 40, 13 * 60 + 25};
            case 8: return new int[]{13 * 60 + 30, 14 * 60 + 15};
            case 9: return new int[]{14 * 60 + 20, 15 * 60 + 5};
            default: return new int[]{0, 0};
        }
    }

    private int parseClockMinutes(String s) {
        int colon = s.indexOf(':');
        if (colon != -1) {
            int h = Integer.parseInt(s.substring(0, colon).trim());
            int m = Integer.parseInt(s.substring(colon + 1).trim());
            return h * 60 + m;
        }
        return 0;
    }

    /**
     * Renders full week table grid (alternative toggle mode).
     */
    private void renderWeekTable(ScheduleData data) {
        tlWeekTable.removeAllViews();
        if (data == null || data.getDays().isEmpty()) return;

        LayoutInflater inflater = LayoutInflater.from(this);

        // 1. Table Header Row (Hours + Days)
        TableRow headerRow = new TableRow(this);

        View hourHeader = inflater.inflate(R.layout.item_table_header_cell, headerRow, false);
        ((TextView) hourHeader.findViewById(R.id.tvHeaderTitle)).setText("Ura");
        ((TextView) hourHeader.findViewById(R.id.tvHeaderSubtitle)).setText("Čas");
        headerRow.addView(hourHeader);

        for (DaySchedule day : data.getDays()) {
            View dayHeader = inflater.inflate(R.layout.item_table_header_cell, headerRow, false);
            TextView title = dayHeader.findViewById(R.id.tvHeaderTitle);
            TextView sub = dayHeader.findViewById(R.id.tvHeaderSubtitle);
            title.setText(day.getDayName());
            sub.setText(day.getDateText());

            if (day.isToday()) {
                dayHeader.setBackgroundResource(R.drawable.bg_table_cell_today);
                title.setTextColor(ContextCompat.getColor(this, R.color.ea_blue));
            }
            headerRow.addView(dayHeader);
        }
        tlWeekTable.addView(headerRow);

        // 2. Data Rows
        int[] range = getVisibleHourRange(data);
        int startHour = range[0];
        int endHour = range[1];

        for (int h = startHour; h <= endHour; h++) {
            TableRow row = new TableRow(this);

            View rowHeader = inflater.inflate(R.layout.item_table_header_cell, row, false);
            String hName = h < data.getHourNames().size() ? data.getHourNames().get(h) : (h + ". ura");
            String hTime = h < data.getHourTimes().size() ? data.getHourTimes().get(h) : "";
            ((TextView) rowHeader.findViewById(R.id.tvHeaderTitle)).setText(hName);
            ((TextView) rowHeader.findViewById(R.id.tvHeaderSubtitle)).setText(hTime);
            row.addView(rowHeader);

            for (DaySchedule day : data.getDays()) {
                View cellView = inflater.inflate(R.layout.item_table_cell, row, false);
                LinearLayout cellItems = cellView.findViewById(R.id.llCellItemsContainer);

                PeriodSchedule period = h < day.getPeriods().size() ? day.getPeriods().get(h) : null;
                boolean isMalica = isMalicaPeriod(period);

                if (day.isToday()) {
                    if (isMalica) {
                        cellView.setBackgroundResource(R.drawable.bg_table_cell_today_malica);
                    } else {
                        cellView.setBackgroundResource(R.drawable.bg_table_cell_today);
                    }
                } else {
                    if (isMalica) {
                        cellView.setBackgroundResource(R.drawable.bg_table_cell_malica);
                    } else {
                        cellView.setBackgroundResource(R.drawable.bg_table_cell);
                    }
                }

                if (isMalica) {
                    View subView = inflater.inflate(R.layout.item_table_subitem, cellItems, false);
                    TextView tvSubj = subView.findViewById(R.id.tvTableSubject);
                    TextView tvProfRoom = subView.findViewById(R.id.tvTableProfessorAndRoom);
                    tvSubj.setText("🍽️ Malica");
                    int malicaColor = ContextCompat.getColor(this, day.isToday() ? R.color.ea_malica_text : R.color.ea_malica_dimmed_text);
                    tvSubj.setTextColor(malicaColor);
                    tvProfRoom.setText("Odmor");
                    tvProfRoom.setTextColor(malicaColor);
                    cellItems.addView(subView);
                } else if (period != null) {
                    for (int i = 0; i < period.getItems().size(); i++) {
                        ClassItem item = period.getItems().get(i);
                        View subView = inflater.inflate(R.layout.item_table_subitem, cellItems, false);
                        TextView tvSubj = subView.findViewById(R.id.tvTableSubject);
                        TextView tvProfRoom = subView.findViewById(R.id.tvTableProfessorAndRoom);
                        TextView tvGroup = subView.findViewById(R.id.tvTableGroup);
                        TextView tvBadge = subView.findViewById(R.id.tvTableBadge);

                        tvSubj.setText(item.getSubject());
                        StringBuilder profAndRoom = new StringBuilder();
                        if (!item.getProfessor().isEmpty()) profAndRoom.append(item.getProfessor());
                        if (!item.getClassroom().isEmpty()) {
                            if (profAndRoom.length() > 0) profAndRoom.append(", ");
                            profAndRoom.append(item.getClassroom());
                        }
                        tvProfRoom.setText(profAndRoom.toString());

                        if (!item.getGroup().isEmpty()) {
                            tvGroup.setText(item.getGroup());
                            tvGroup.setVisibility(View.VISIBLE);
                        }
                        if (!item.getBadge().isEmpty()) {
                            tvBadge.setText(item.getBadge());
                            tvBadge.setVisibility(View.VISIBLE);
                        }
                        cellItems.addView(subView);

                        // Line between classes that happen in the same time period
                        if (i < period.getItems().size() - 1) {
                            View divider = new View(this);
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1.5f));
                            lp.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
                            divider.setLayoutParams(lp);
                            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.ea_class_divider));
                            cellItems.addView(divider);
                        }
                    }
                }

                row.addView(cellView);
            }
            tlWeekTable.addView(row);
        }
    }

    private void scrollToCenterTodayInTable() {
        if (currentSchedule == null || hsvWeekTable == null) return;
        hsvWeekTable.post(() -> {
            int todayIdx = getTodayDayIndex(currentSchedule);
            int screenWidth = hsvWeekTable.getWidth();
            if (screenWidth <= 0) return;

            float density = getResources().getDisplayMetrics().density;
            int headerColWidthPx = (int) (120 * density);
            int dayColWidthPx = (int) (120 * density);

            int columnCenterPx = headerColWidthPx + (todayIdx * dayColWidthPx) + (dayColWidthPx / 2);
            int targetScrollX = columnCenterPx - (screenWidth / 2);
            if (targetScrollX < 0) targetScrollX = 0;

            hsvWeekTable.smoothScrollTo(targetScrollX, 0);
        });
    }

    private void refreshScheduleFromNetwork() {
        final String url = UrnikStorage.getSavedUrl(this);
        if (url == null || url.trim().isEmpty()) {
            showSettingsDialog();
            return;
        }

        pbLoading.setVisibility(View.VISIBLE);
        btnRefresh.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                String html = UrnikFetcher.fetchHtmlSync(url);
                ScheduleData freshSchedule = UrnikParser.parse(html, url);

                if (freshSchedule != null && !freshSchedule.getDays().isEmpty()) {
                    if (isWeekend()) {
                        int curWeek = freshSchedule.getCurrentWeekNumber();
                        int nextWeek = curWeek > 0 ? curWeek + 1 : 0;
                        if (nextWeek > 0 && (freshSchedule.getTotalWeeks() == 0 || nextWeek <= freshSchedule.getTotalWeeks())) {
                            // On Saturday/Sunday, automatically fetch next week and open on Monday
                            final String baseUrl = UrnikStorage.getSavedUrl(MainActivity.this);
                            int idSola = freshSchedule.getIdSola();
                            if (idSola == 0) idSola = 224;
                            final String ajaxUrl = UrnikFetcher.buildAjaxWeekUrl(baseUrl, idSola, nextWeek);
                            if (ajaxUrl != null) {
                                try {
                                    String nextWeekResponse = UrnikFetcher.fetchWeekAjaxSync(ajaxUrl);
                                    ScheduleData nextWeekSchedule = UrnikParser.parseAjaxWeek(nextWeekResponse, baseUrl, freshSchedule);
                                    if (nextWeekSchedule != null && !nextWeekSchedule.getDays().isEmpty()) {
                                        UrnikStorage.saveSchedule(MainActivity.this, nextWeekSchedule);
                                        mainHandler.post(() -> {
                                            pbLoading.setVisibility(View.GONE);
                                            btnRefresh.setVisibility(View.VISIBLE);
                                            displaySchedule(nextWeekSchedule);
                                            focusedDayIndex = 0; // Monday
                                            if (!isTableView) {
                                                renderThreeDayView(nextWeekSchedule, 0);
                                            }
                                        });
                                        return;
                                    }
                                } catch (Exception ignored) {
                                    // Fallback to base week if next week fetch fails
                                }
                            }
                        }
                    }

                    UrnikStorage.saveSchedule(MainActivity.this, freshSchedule);

                    mainHandler.post(() -> {
                        pbLoading.setVisibility(View.GONE);
                        btnRefresh.setVisibility(View.VISIBLE);
                        displaySchedule(freshSchedule);
                    });
                } else {
                    throw new Exception("Podatkov o urniku ni bilo mogoče prebrati.");
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    pbLoading.setVisibility(View.GONE);
                    btnRefresh.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void showSettingsDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        EditText etUrl = dialogView.findViewById(R.id.etUrl);
        MaterialSwitch swDarkMode = dialogView.findViewById(R.id.swDarkMode);
        MaterialSwitch swRefreshPosition = dialogView.findViewById(R.id.swRefreshPosition);

        etUrl.setText(UrnikStorage.getSavedUrl(this));
        if (etUrl.getText() != null) {
            etUrl.setSelection(etUrl.getText().length());
        }

        swDarkMode.setChecked(UrnikStorage.isDarkMode(this));
        swDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UrnikStorage.setDarkMode(MainActivity.this, isChecked);
            AppCompatDelegate.setDefaultNightMode(isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        View llDarkModeRow = dialogView.findViewById(R.id.llDarkModeRow);
        if (llDarkModeRow != null) {
            llDarkModeRow.setOnClickListener(v -> swDarkMode.toggle());
        }

        if (swRefreshPosition != null) {
            swRefreshPosition.setChecked(UrnikStorage.isRefreshButtonLeft(this));
            swRefreshPosition.setOnCheckedChangeListener((buttonView, isChecked) -> {
                UrnikStorage.setRefreshButtonLeft(MainActivity.this, isChecked);
                updateRefreshButtonPosition();
            });
        }

        View llRefreshPositionRow = dialogView.findViewById(R.id.llRefreshPositionRow);
        if (llRefreshPositionRow != null && swRefreshPosition != null) {
            llRefreshPositionRow.setOnClickListener(v -> swRefreshPosition.toggle());
        }

        builder.setView(dialogView)
                .setPositiveButton("Shrani in osveži", (dialog, which) -> {
                    String newUrl = etUrl.getText().toString().trim();
                    if (!newUrl.isEmpty()) {
                        UrnikStorage.saveUrl(MainActivity.this, newUrl);
                        refreshScheduleFromNetwork();
                    }
                })
                .setNegativeButton("Zapri", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        executor.shutdown();
    }
}