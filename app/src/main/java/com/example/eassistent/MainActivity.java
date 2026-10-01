package com.example.eassistent;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
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
    private ImageButton btnMenu;
    private FrameLayout fabRefreshContainer;
    private ImageButton btnRefresh;
    private ProgressBar pbLoading;

    // 1. Three-Day Focus View
    private LinearLayout layoutThreeDayView;
    private LinearLayout llHeaderYesterday;
    private TextView tvHeaderYesterdayBadge;
    private TextView tvHeaderYesterdayName;
    private TextView tvHeaderYesterdayDate;

    private LinearLayout llHeaderToday;
    private TextView tvHeaderTodayBadge;
    private TextView tvHeaderTodayName;
    private TextView tvHeaderTodayDate;

    private LinearLayout llHeaderTomorrow;
    private TextView tvHeaderTomorrowBadge;
    private TextView tvHeaderTomorrowName;
    private TextView tvHeaderTomorrowDate;

    // 3 Columns (Rounded by Column!)
    private MaterialCardView cardYesterdayColumn;
    private LinearLayout llYesterdayColumn;
    private MaterialCardView cardTodayColumn;
    private LinearLayout llTodayColumn;
    private MaterialCardView cardTomorrowColumn;
    private LinearLayout llTomorrowColumn;
    private LinearLayout llGradientOverlays;

    // 2. Full Week Table View
    private LinearLayout layoutTableView;
    private HorizontalScrollView hsvWeekTable;
    private TableLayout tlWeekTable;

    // State & Gestures
    private ScheduleData currentSchedule;
    private int focusedDayIndex = 0;
    private boolean isTableView = false; // Primary default view is 3-Day Focus View
    private GestureDetector gestureDetector;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        setupGestureDetector();
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
        btnMenu = findViewById(R.id.btnMenu);
        fabRefreshContainer = findViewById(R.id.fabRefreshContainer);
        btnRefresh = findViewById(R.id.btnRefresh);
        pbLoading = findViewById(R.id.pbLoading);

        updateRefreshButtonPosition();

        // 3-Day Focus View
        layoutThreeDayView = findViewById(R.id.layoutThreeDayView);
        llHeaderYesterday = findViewById(R.id.llHeaderYesterday);
        tvHeaderYesterdayBadge = findViewById(R.id.tvHeaderYesterdayBadge);
        tvHeaderYesterdayName = findViewById(R.id.tvHeaderYesterdayName);
        tvHeaderYesterdayDate = findViewById(R.id.tvHeaderYesterdayDate);

        llHeaderToday = findViewById(R.id.llHeaderToday);
        tvHeaderTodayBadge = findViewById(R.id.tvHeaderTodayBadge);
        tvHeaderTodayName = findViewById(R.id.tvHeaderTodayName);
        tvHeaderTodayDate = findViewById(R.id.tvHeaderTodayDate);

        llHeaderTomorrow = findViewById(R.id.llHeaderTomorrow);
        tvHeaderTomorrowBadge = findViewById(R.id.tvHeaderTomorrowBadge);
        tvHeaderTomorrowName = findViewById(R.id.tvHeaderTomorrowName);
        tvHeaderTomorrowDate = findViewById(R.id.tvHeaderTomorrowDate);

        // 3 Column Cards (Rounded by Column!)
        cardYesterdayColumn = findViewById(R.id.cardYesterdayColumn);
        llYesterdayColumn = findViewById(R.id.llYesterdayColumn);
        cardTodayColumn = findViewById(R.id.cardTodayColumn);
        llTodayColumn = findViewById(R.id.llTodayColumn);
        cardTomorrowColumn = findViewById(R.id.cardTomorrowColumn);
        llTomorrowColumn = findViewById(R.id.llTomorrowColumn);
        llGradientOverlays = findViewById(R.id.llGradientOverlays);

        // Ensure gradient overlay lets touches pass through to scroll view underneath
        if (llGradientOverlays != null) {
            llGradientOverlays.setOnTouchListener((v, event) -> false);
        }

        // Week Table View
        layoutTableView = findViewById(R.id.layoutTableView);
        hsvWeekTable = findViewById(R.id.hsvWeekTable);
        tlWeekTable = findViewById(R.id.tlWeekTable);

        updateViewVisibility();
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_MIN_DISTANCE = 80;
            private static final int SWIPE_THRESHOLD_VELOCITY = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (isTableView || e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                        if (diffX > 0) {
                            // Swiped right -> go to previous day
                            navigateDay(-1);
                        } else {
                            // Swiped left -> go to next day
                            navigateDay(1);
                        }
                        return true;
                    }
                }
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null && !isTableView) {
            if (gestureDetector.onTouchEvent(ev)) {
                return true;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void navigateDay(int delta) {
        if (currentSchedule != null && !currentSchedule.getDays().isEmpty()) {
            int newIndex = focusedDayIndex + delta;
            if (newIndex >= 0 && newIndex < currentSchedule.getDays().size()) {
                focusedDayIndex = newIndex;
                renderThreeDayView(currentSchedule, focusedDayIndex);
            }
        }
    }

    private void setupListeners() {
        btnRefresh.setOnClickListener(v -> refreshScheduleFromNetwork());

        btnMenu.setOnClickListener(this::showPopupMenu);

        btnPrevDay.setOnClickListener(v -> navigateDay(-1));
        btnNextDay.setOnClickListener(v -> navigateDay(1));

        btnJumpToday.setOnClickListener(v -> {
            if (currentSchedule != null) {
                focusedDayIndex = getTodayDayIndex(currentSchedule);
                renderThreeDayView(currentSchedule, focusedDayIndex);
            }
        });

        llHeaderYesterday.setOnClickListener(v -> navigateDay(-1));
        llHeaderTomorrow.setOnClickListener(v -> navigateDay(1));
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
        layoutThreeDayView.setVisibility(!isTableView ? View.VISIBLE : View.GONE);
        layoutTableView.setVisibility(isTableView ? View.VISIBLE : View.GONE);
    }

    private void loadCachedSchedule() {
        ScheduleData cached = UrnikStorage.loadSchedule(this);
        if (cached != null) {
            displaySchedule(cached);
        } else {
            refreshScheduleFromNetwork();
        }
    }

    private void displaySchedule(ScheduleData data) {
        this.currentSchedule = data;
        if (data == null || data.getDays().isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            layoutThreeDayView.setVisibility(View.GONE);
            layoutTableView.setVisibility(View.GONE);
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

    private int getTodayDayIndex(ScheduleData data) {
        if (data == null || data.getDays().isEmpty()) return 0;
        for (int i = 0; i < data.getDays().size(); i++) {
            if (data.getDays().get(i).isToday()) {
                return i;
            }
        }
        // Fallback by Calendar DAY_OF_WEEK (MONDAY = 0)
        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        int mapped = dow - Calendar.MONDAY;
        if (mapped >= 0 && mapped < data.getDays().size()) {
            return mapped;
        }
        return 0;
    }

    /**
     * Renders the 3-column split view (Yesterday, Current Day, Tomorrow).
     * Highlights Current Day and dims the other two.
     * Highlights currently active hour, and next hour during break time in less strong color.
     */
    private void renderThreeDayView(ScheduleData data, int centerIdx) {
        if (data == null || data.getDays().isEmpty()) return;

        int totalDays = data.getDays().size();
        if (centerIdx < 0) centerIdx = 0;
        if (centerIdx >= totalDays) centerIdx = totalDays - 1;

        DaySchedule centerDay = data.getDays().get(centerIdx);
        DaySchedule yesterdayDay = centerIdx > 0 ? data.getDays().get(centerIdx - 1) : null;
        DaySchedule tomorrowDay = centerIdx < totalDays - 1 ? data.getDays().get(centerIdx + 1) : null;

        // 1. Configure Header 1 (Yesterday)
        if (yesterdayDay != null) {
            llHeaderYesterday.setVisibility(View.VISIBLE);
            tvHeaderYesterdayBadge.setText(centerIdx == getTodayDayIndex(data) ? "VČERAJ" : "PREJŠNJI DAN");
            tvHeaderYesterdayName.setText(yesterdayDay.getDayName());
            tvHeaderYesterdayDate.setText(yesterdayDay.getDateText());
            llHeaderYesterday.setAlpha(0.78f);
        } else {
            llHeaderYesterday.setVisibility(View.VISIBLE);
            tvHeaderYesterdayBadge.setText("PREJŠNJI");
            tvHeaderYesterdayName.setText("—");
            tvHeaderYesterdayDate.setText("Ni podatka");
            llHeaderYesterday.setAlpha(0.5f);
        }

        // 2. Configure Header 2 (Current Day - Highlighted!)
        llHeaderToday.setVisibility(View.VISIBLE);
        boolean isActualToday = centerDay.isToday() || centerIdx == getTodayDayIndex(data);
        tvHeaderTodayBadge.setText(isActualToday ? "⭐ DANES" : "IZBRANI DAN");
        tvHeaderTodayName.setText(centerDay.getDayName());
        tvHeaderTodayDate.setText(centerDay.getDateText());
        llHeaderToday.setAlpha(1.0f);

        // 3. Configure Header 3 (Tomorrow)
        if (tomorrowDay != null) {
            llHeaderTomorrow.setVisibility(View.VISIBLE);
            tvHeaderTomorrowBadge.setText(centerIdx == getTodayDayIndex(data) ? "JUTRI" : "NASLEDNJI DAN");
            tvHeaderTomorrowName.setText(tomorrowDay.getDayName());
            tvHeaderTomorrowDate.setText(tomorrowDay.getDateText());
            llHeaderTomorrow.setAlpha(0.78f);
        } else {
            llHeaderTomorrow.setVisibility(View.VISIBLE);
            tvHeaderTomorrowBadge.setText("NASLEDNJI");
            tvHeaderTomorrowName.setText("—");
            tvHeaderTomorrowDate.setText("Ni podatka");
            llHeaderTomorrow.setAlpha(0.5f);
        }

        // Determine active period and next period during break for the center day
        Calendar now = Calendar.getInstance();
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        int activePeriodIdx = -1;
        int nextPeriodIdx = -1;

        if (isActualToday) {
            // Find active period
            for (int i = 0; i < centerDay.getPeriods().size(); i++) {
                PeriodSchedule p = centerDay.getPeriods().get(i);
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

        // 4. Render Hours into the 3 Unified Columns
        llYesterdayColumn.removeAllViews();
        llTodayColumn.removeAllViews();
        llTomorrowColumn.removeAllViews();

        cardYesterdayColumn.setAlpha(yesterdayDay != null ? 0.78f : 0.45f);
        cardTodayColumn.setAlpha(1.0f);
        cardTomorrowColumn.setAlpha(tomorrowDay != null ? 0.78f : 0.45f);

        int[] range = getVisibleHourRange(data);
        int startHour = range[0];
        int endHour = range[1];

        LayoutInflater inflater = LayoutInflater.from(this);

        for (int h = startHour; h <= endHour; h++) {
            PeriodSchedule centerPeriod = h < centerDay.getPeriods().size() ? centerDay.getPeriods().get(h) : null;
            PeriodSchedule yesterdayPeriod = (yesterdayDay != null && h < yesterdayDay.getPeriods().size()) ? yesterdayDay.getPeriods().get(h) : null;
            PeriodSchedule tomorrowPeriod = (tomorrowDay != null && h < tomorrowDay.getPeriods().size()) ? tomorrowDay.getPeriods().get(h) : null;

            int maxClassesInTimespan = 0;
            if (centerPeriod != null && centerPeriod.getItems() != null) {
                maxClassesInTimespan = Math.max(maxClassesInTimespan, centerPeriod.getItems().size());
            }
            if (yesterdayPeriod != null && yesterdayPeriod.getItems() != null) {
                maxClassesInTimespan = Math.max(maxClassesInTimespan, yesterdayPeriod.getItems().size());
            }
            if (tomorrowPeriod != null && tomorrowPeriod.getItems() != null) {
                maxClassesInTimespan = Math.max(maxClassesInTimespan, tomorrowPeriod.getItems().size());
            }

            float rowWeight = (maxClassesInTimespan >= 2) ? 1.75f : 1.0f;

            // Add thin divider between consecutive hours in each column
            if (h > startHour) {
                addHourDivider(llYesterdayColumn);
                addHourDivider(llTodayColumn);
                addHourDivider(llTomorrowColumn);
            }

            // Check State of this hour on the center day
            PeriodHourState state = PeriodHourState.FUTURE;
            boolean isMalica = isMalicaPeriod(centerPeriod);

            if (isActualToday) {
                if (h == activePeriodIdx) {
                    state = PeriodHourState.CURRENTLY_ACTIVE;
                } else if (activePeriodIdx == -1 && h == nextPeriodIdx) {
                    state = PeriodHourState.NEXT_DURING_BREAK;
                } else if (centerPeriod != null) {
                    int[] se = parseStartEndMinutes(centerPeriod);
                    if (se[1] > 0 && nowMinutes >= se[1]) {
                        state = PeriodHourState.PASSED;
                    }
                }
            }

            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, rowWeight);

            // Render Center Cell (Today - Highlighted)
            View todayCell = createCellView(centerPeriod, true, state, isMalica, inflater, llTodayColumn);
            todayCell.setLayoutParams(cellLp);
            llTodayColumn.addView(todayCell);

            // Render Left Cell (Yesterday - Dimmed)
            boolean yMalica = isMalicaPeriod(yesterdayPeriod);
            View yesterdayCell = createCellView(yesterdayPeriod, false, PeriodHourState.PASSED, yMalica, inflater, llYesterdayColumn);
            yesterdayCell.setLayoutParams(new LinearLayout.LayoutParams(cellLp));
            llYesterdayColumn.addView(yesterdayCell);

            // Render Right Cell (Tomorrow - Dimmed)
            boolean tMalica = isMalicaPeriod(tomorrowPeriod);
            View tomorrowCell = createCellView(tomorrowPeriod, false, PeriodHourState.FUTURE, tMalica, inflater, llTomorrowColumn);
            tomorrowCell.setLayoutParams(new LinearLayout.LayoutParams(cellLp));
            llTomorrowColumn.addView(tomorrowCell);
        }
    }

    private void addHourDivider(LinearLayout col) {
        View div = new View(this);
        div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
        div.setBackgroundColor(ContextCompat.getColor(this, R.color.ea_card_border));
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

            if (isTodayColumn) {
                llCellRoot.setBackgroundResource(R.drawable.bg_card_malica);
            } else {
                llCellRoot.setBackgroundResource(R.drawable.bg_cell_malica_dimmed);
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

            // Line between classes that happen in the same time period
            if (i < period.getItems().size() - 1) {
                View divider = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1.5f));
                lp.setMargins(dpToPx(4), dpToPx(2.5f), dpToPx(4), dpToPx(2.5f));
                divider.setLayoutParams(lp);
                int divColor;
                if (isTodayColumn && state == PeriodHourState.CURRENTLY_ACTIVE) {
                    divColor = ContextCompat.getColor(this, R.color.ea_hour_active_border);
                } else {
                    divColor = ContextCompat.getColor(this, R.color.ea_class_divider);
                }
                divider.setBackgroundColor(divColor);
                llCellItems.addView(divider);
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
                    tvSubj.setTextColor(ContextCompat.getColor(this, R.color.ea_malica_text));
                    tvProfRoom.setText("Odmor");
                    tvProfRoom.setTextColor(ContextCompat.getColor(this, R.color.ea_malica_text));
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
        executor.shutdown();
    }
}