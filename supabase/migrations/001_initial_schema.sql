-- GLA CBT - Green Light Academy School Management & CBT Platform
-- Complete PostgreSQL Schema, Indexes, Constraints, Storage & Row Level Security (RLS)

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Helper Enum / Domain Checks
-- Roles: admin, teacher, student, parent

-- 2. Academic Sessions & School Settings
CREATE TABLE IF NOT EXISTS public.academic_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL, -- e.g. "2026/2027"
    term TEXT NOT NULL CHECK (term IN ('First Term', 'Second Term', 'Third Term')),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (name, term)
);

CREATE TABLE IF NOT EXISTS public.school_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_name TEXT NOT NULL DEFAULT 'Green Light Academy',
    motto TEXT NOT NULL DEFAULT 'Excellence Through Knowledge & Integrity',
    logo_url TEXT,
    address TEXT DEFAULT 'Plot 14 Academy Way, Victoria Island, Lagos',
    contact_email TEXT DEFAULT 'portal@greenlightacademy.edu.ng',
    contact_phone TEXT DEFAULT '+234 800 452 2280',
    default_exam_duration_minutes INT NOT NULL DEFAULT 60 CHECK (default_exam_duration_minutes > 0),
    default_pass_mark NUMERIC(5,2) NOT NULL DEFAULT 50.00 CHECK (default_pass_mark >= 0 AND default_pass_mark <= 100),
    max_tab_switches INT NOT NULL DEFAULT 3 CHECK (max_tab_switches >= 1),
    allow_instant_results BOOLEAN NOT NULL DEFAULT TRUE,
    allow_exam_resume BOOLEAN NOT NULL DEFAULT TRUE,
    paystack_subaccount_code TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. Profiles (linked to auth.users)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL UNIQUE,
    full_name TEXT NOT NULL,
    phone TEXT,
    role TEXT NOT NULL CHECK (role IN ('admin', 'teacher', 'student', 'parent')),
    avatar_url TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_profiles_role ON public.profiles(role);
CREATE INDEX IF NOT EXISTS idx_profiles_active ON public.profiles(is_active);

-- 4. Classes & Subjects
CREATE TABLE IF NOT EXISTS public.classes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE, -- e.g., "JSS 1A", "SSS 2 Science"
    level TEXT NOT NULL,       -- e.g., "JSS 1", "SSS 2"
    department TEXT NOT NULL DEFAULT 'General', -- General, Science, Commercial, Arts
    capacity INT NOT NULL DEFAULT 40 CHECK (capacity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.teachers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL UNIQUE REFERENCES public.profiles(id) ON DELETE CASCADE,
    staff_code TEXT NOT NULL UNIQUE,
    department TEXT NOT NULL DEFAULT 'General',
    qualification TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.subjects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL UNIQUE, -- e.g. "MTH-101"
    name TEXT NOT NULL,
    department TEXT NOT NULL DEFAULT 'General',
    class_id UUID REFERENCES public.classes(id) ON DELETE SET NULL,
    teacher_id UUID REFERENCES public.teachers(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_subjects_class ON public.subjects(class_id);
CREATE INDEX IF NOT EXISTS idx_subjects_teacher ON public.subjects(teacher_id);

-- 5. Students & Parents
CREATE TABLE IF NOT EXISTS public.students (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL UNIQUE REFERENCES public.profiles(id) ON DELETE CASCADE,
    admission_number TEXT NOT NULL UNIQUE,
    class_id UUID REFERENCES public.classes(id) ON DELETE SET NULL,
    department TEXT NOT NULL DEFAULT 'General',
    passport_url TEXT,
    gender TEXT CHECK (gender IN ('Male', 'Female', 'Other')),
    date_of_birth DATE,
    admission_status TEXT NOT NULL DEFAULT 'approved' CHECK (admission_status IN ('pending', 'approved', 'suspended', 'graduated')),
    guardian_contact TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_students_class ON public.students(class_id);
CREATE INDEX IF NOT EXISTS idx_students_adm_no ON public.students(admission_number);

CREATE TABLE IF NOT EXISTS public.parents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL UNIQUE REFERENCES public.profiles(id) ON DELETE CASCADE,
    occupation TEXT,
    address TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.parent_student_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID NOT NULL REFERENCES public.parents(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES public.students(id) ON DELETE CASCADE,
    relationship TEXT NOT NULL DEFAULT 'Guardian',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (parent_id, student_id)
);

-- 6. Enrollments
CREATE TABLE IF NOT EXISTS public.enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES public.students(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES public.classes(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES public.subjects(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES public.academic_sessions(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (student_id, subject_id, session_id)
);

CREATE INDEX IF NOT EXISTS idx_enrollments_student ON public.enrollments(student_id);

-- 7. Examinations, Questions & Answer Options
CREATE TABLE IF NOT EXISTS public.examinations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    instructions TEXT NOT NULL DEFAULT 'Read every question carefully. Do not switch apps or exit full-screen mode during the exam.',
    subject_id UUID NOT NULL REFERENCES public.subjects(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES public.classes(id) ON DELETE CASCADE,
    session_id UUID REFERENCES public.academic_sessions(id) ON DELETE SET NULL,
    created_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    duration_minutes INT NOT NULL CHECK (duration_minutes > 0),
    pass_mark NUMERIC(5,2) NOT NULL DEFAULT 50.00 CHECK (pass_mark >= 0 AND pass_mark <= 100),
    total_marks NUMERIC(6,2) NOT NULL DEFAULT 100.00 CHECK (total_marks > 0),
    scheduled_start TIMESTAMPTZ NOT NULL,
    scheduled_end TIMESTAMPTZ NOT NULL,
    randomize_questions BOOLEAN NOT NULL DEFAULT TRUE,
    randomize_options BOOLEAN NOT NULL DEFAULT TRUE,
    instant_results_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    allow_resume BOOLEAN NOT NULL DEFAULT TRUE,
    max_tab_switches INT NOT NULL DEFAULT 3,
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    results_published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (scheduled_end > scheduled_start)
);

CREATE INDEX IF NOT EXISTS idx_exams_class_published ON public.examinations(class_id, is_published);
CREATE INDEX IF NOT EXISTS idx_exams_subject ON public.examinations(subject_id);

CREATE TABLE IF NOT EXISTS public.questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    examination_id UUID NOT NULL REFERENCES public.examinations(id) ON DELETE CASCADE,
    question_type TEXT NOT NULL CHECK (question_type IN ('mcq', 'true_false', 'fill_gap', 'theory')),
    prompt TEXT NOT NULL,
    image_url TEXT,
    marks NUMERIC(5,2) NOT NULL DEFAULT 1.00 CHECK (marks > 0),
    correct_text_answer TEXT, -- Used for fill_gap or true_false canonical reference
    marking_guide TEXT,       -- Used by teachers for theory grading
    order_index INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_questions_exam ON public.questions(examination_id);

CREATE TABLE IF NOT EXISTS public.answer_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL REFERENCES public.questions(id) ON DELETE CASCADE,
    option_text TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    order_index INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_answer_options_question ON public.answer_options(question_id);

-- 8. Exam Attempts, Student Answers & Anti-Cheat Logs
CREATE TABLE IF NOT EXISTS public.exam_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    examination_id UUID NOT NULL REFERENCES public.examinations(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES public.students(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'in_progress' CHECK (status IN ('in_progress', 'submitted', 'graded')),
    seed BIGINT NOT NULL DEFAULT floor(random() * 1000000)::BIGINT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    submitted_at TIMESTAMPTZ,
    remaining_seconds INT NOT NULL,
    tab_switch_count INT NOT NULL DEFAULT 0,
    objective_score NUMERIC(6,2) NOT NULL DEFAULT 0,
    theory_score NUMERIC(6,2) NOT NULL DEFAULT 0,
    total_score NUMERIC(6,2) NOT NULL DEFAULT 0,
    UNIQUE (examination_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_attempts_exam ON public.exam_attempts(examination_id);
CREATE INDEX IF NOT EXISTS idx_attempts_student ON public.exam_attempts(student_id);

CREATE TABLE IF NOT EXISTS public.student_answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id UUID NOT NULL REFERENCES public.exam_attempts(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES public.questions(id) ON DELETE CASCADE,
    selected_option_id UUID REFERENCES public.answer_options(id) ON DELETE SET NULL,
    text_response TEXT,
    is_marked_for_review BOOLEAN NOT NULL DEFAULT FALSE,
    is_correct BOOLEAN,
    marks_awarded NUMERIC(5,2) NOT NULL DEFAULT 0,
    teacher_feedback TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (attempt_id, question_id)
);

CREATE INDEX IF NOT EXISTS idx_student_answers_attempt ON public.student_answers(attempt_id);

-- 9. Results (CA + Exam = 100, Class Position, Admin Approval)
CREATE TABLE IF NOT EXISTS public.results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES public.students(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES public.subjects(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES public.classes(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES public.academic_sessions(id) ON DELETE CASCADE,
    ca_score NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (ca_score >= 0 AND ca_score <= 40),
    exam_score NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (exam_score >= 0 AND exam_score <= 60),
    total_score NUMERIC(5,2) GENERATED ALWAYS AS (ca_score + exam_score) STORED,
    grade TEXT NOT NULL,
    remark TEXT NOT NULL,
    class_position INT,
    is_approved BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (student_id, subject_id, session_id)
);

CREATE INDEX IF NOT EXISTS idx_results_student_session ON public.results(student_id, session_id);
CREATE INDEX IF NOT EXISTS idx_results_class_approved ON public.results(class_id, is_approved);

-- 10. Attendance
CREATE TABLE IF NOT EXISTS public.attendance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES public.students(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES public.classes(id) ON DELETE CASCADE,
    session_id UUID REFERENCES public.academic_sessions(id) ON DELETE SET NULL,
    date DATE NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('present', 'absent', 'late', 'excused')),
    recorded_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (student_id, date)
);

CREATE INDEX IF NOT EXISTS idx_attendance_student_date ON public.attendance(student_id, date DESC);

-- 11. Assignments, Submissions & Learning Materials
CREATE TABLE IF NOT EXISTS public.assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    subject_id UUID NOT NULL REFERENCES public.subjects(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES public.classes(id) ON DELETE CASCADE,
    teacher_id UUID NOT NULL REFERENCES public.teachers(id) ON DELETE CASCADE,
    attachment_url TEXT,
    due_date TIMESTAMPTZ NOT NULL,
    max_score NUMERIC(5,2) NOT NULL DEFAULT 20,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.assignment_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID NOT NULL REFERENCES public.assignments(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES public.students(id) ON DELETE CASCADE,
    submission_text TEXT,
    attachment_url TEXT,
    score NUMERIC(5,2),
    feedback TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (assignment_id, student_id)
);

CREATE TABLE IF NOT EXISTS public.learning_materials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    description TEXT,
    subject_id UUID NOT NULL REFERENCES public.subjects(id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES public.classes(id) ON DELETE CASCADE,
    uploaded_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    file_url TEXT NOT NULL,
    file_type TEXT NOT NULL DEFAULT 'PDF',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 12. Announcements, Timetable, Calendar & Parent-Teacher Messages
CREATE TABLE IF NOT EXISTS public.announcements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    target_role TEXT NOT NULL DEFAULT 'all' CHECK (target_role IN ('all', 'student', 'teacher', 'parent', 'admin')),
    priority TEXT NOT NULL DEFAULT 'normal' CHECK (priority IN ('normal', 'urgent')),
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.timetable_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID NOT NULL REFERENCES public.classes(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES public.subjects(id) ON DELETE CASCADE,
    day_of_week TEXT NOT NULL CHECK (day_of_week IN ('Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday')),
    start_time TEXT NOT NULL, -- "08:00"
    end_time TEXT NOT NULL,   -- "08:45"
    room TEXT DEFAULT 'Block A'
);

CREATE TABLE IF NOT EXISTS public.calendar_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    description TEXT,
    event_date DATE NOT NULL,
    category TEXT NOT NULL DEFAULT 'Academic' CHECK (category IN ('Academic', 'Examination', 'Holiday', 'PTA'))
);

CREATE TABLE IF NOT EXISTS public.parent_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_profile_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    teacher_profile_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    student_id UUID REFERENCES public.students(id) ON DELETE SET NULL,
    subject TEXT NOT NULL,
    body TEXT NOT NULL,
    reply_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 13. Invoices & Paystack Payments
CREATE TABLE IF NOT EXISTS public.invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES public.students(id) ON DELETE CASCADE,
    session_id UUID REFERENCES public.academic_sessions(id) ON DELETE SET NULL,
    title TEXT NOT NULL, -- e.g., "First Term Tuition Fee", "WAEC/CBT Exam Fee"
    fee_type TEXT NOT NULL CHECK (fee_type IN ('school_fee', 'exam_fee', 'other')),
    amount_kobo BIGINT NOT NULL CHECK (amount_kobo > 0),
    amount_paid_kobo BIGINT NOT NULL DEFAULT 0 CHECK (amount_paid_kobo >= 0),
    status TEXT NOT NULL DEFAULT 'unpaid' CHECK (status IN ('unpaid', 'partial', 'paid')),
    due_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_invoices_student ON public.invoices(student_id);

CREATE TABLE IF NOT EXISTS public.payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES public.invoices(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES public.students(id) ON DELETE CASCADE,
    paid_by_profile_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    reference TEXT NOT NULL UNIQUE,
    amount_kobo BIGINT NOT NULL CHECK (amount_kobo > 0),
    currency TEXT NOT NULL DEFAULT 'NGN',
    channel TEXT DEFAULT 'paystack',
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'verified', 'failed')),
    gateway_response TEXT,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payments_invoice ON public.payments(invoice_id);
CREATE INDEX IF NOT EXISTS idx_payments_ref ON public.payments(reference);

-- 14. Notifications & Audit Logs
CREATE TABLE IF NOT EXISTS public.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'general' CHECK (category IN ('exam', 'result', 'fee', 'announcement', 'general')),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user ON public.notifications(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT,
    metadata JSONB DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON public.audit_logs(created_at DESC);

-- 15. Security Helper Functions
CREATE OR REPLACE FUNCTION public.current_user_role()
RETURNS TEXT
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT role FROM public.profiles WHERE id = auth.uid() AND is_active = TRUE LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role = 'admin' AND is_active = TRUE
    );
$$;

CREATE OR REPLACE FUNCTION public.is_teacher()
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = auth.uid() AND role = 'teacher' AND is_active = TRUE
    );
$$;

CREATE OR REPLACE FUNCTION public.is_linked_parent(target_student_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.parent_student_links psl
        JOIN public.parents p ON p.id = psl.parent_id
        WHERE p.profile_id = auth.uid() AND psl.student_id = target_student_id
    );
$$;

-- 16. Enable Row Level Security (RLS) on All Tables
ALTER TABLE public.academic_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.school_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.teachers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.subjects ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.students ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.parents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.parent_student_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.enrollments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.examinations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.answer_options ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.exam_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.student_answers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.results ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.assignment_submissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.learning_materials ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.announcements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.timetable_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.calendar_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.parent_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

-- 17. RLS Policies

-- Profiles: users can view/update own profile; admins manage all; teachers/parents can view directory names
CREATE POLICY "profiles_select_authenticated" ON public.profiles
    FOR SELECT TO authenticated USING (TRUE);

CREATE POLICY "profiles_insert_own" ON public.profiles
    FOR INSERT TO authenticated WITH CHECK (id = auth.uid() OR public.is_admin());

CREATE POLICY "profiles_update_own_or_admin" ON public.profiles
    FOR UPDATE TO authenticated USING (id = auth.uid() OR public.is_admin())
    WITH CHECK (id = auth.uid() OR public.is_admin());

-- School settings & Academic Sessions & Classes & Subjects & Timetable & Calendar
CREATE POLICY "settings_read_all" ON public.school_settings FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "settings_admin_write" ON public.school_settings FOR ALL TO authenticated USING (public.is_admin()) WITH CHECK (public.is_admin());

CREATE POLICY "sessions_read_all" ON public.academic_sessions FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "sessions_admin_write" ON public.academic_sessions FOR ALL TO authenticated USING (public.is_admin()) WITH CHECK (public.is_admin());

CREATE POLICY "classes_read_all" ON public.classes FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "classes_admin_write" ON public.classes FOR ALL TO authenticated USING (public.is_admin()) WITH CHECK (public.is_admin());

CREATE POLICY "subjects_read_all" ON public.subjects FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "subjects_admin_write" ON public.subjects FOR ALL TO authenticated USING (public.is_admin()) WITH CHECK (public.is_admin());

CREATE POLICY "timetable_read_all" ON public.timetable_entries FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "timetable_admin_write" ON public.timetable_entries FOR ALL TO authenticated USING (public.is_admin()) WITH CHECK (public.is_admin());

CREATE POLICY "calendar_read_all" ON public.calendar_events FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "calendar_admin_write" ON public.calendar_events FOR ALL TO authenticated USING (public.is_admin()) WITH CHECK (public.is_admin());

-- Teachers
CREATE POLICY "teachers_read_all" ON public.teachers FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "teachers_insert_self_or_admin" ON public.teachers FOR INSERT TO authenticated WITH CHECK (profile_id = auth.uid() OR public.is_admin());
CREATE POLICY "teachers_admin_write" ON public.teachers FOR UPDATE TO authenticated USING (public.is_admin() OR profile_id = auth.uid());

-- Students: students see self; linked parents see child; teachers & admins see students
CREATE POLICY "students_select_scoped" ON public.students
    FOR SELECT TO authenticated
    USING (
        profile_id = auth.uid()
        OR public.is_admin()
        OR public.is_teacher()
        OR public.is_linked_parent(id)
    );
CREATE POLICY "students_insert_self_or_admin" ON public.students
    FOR INSERT TO authenticated WITH CHECK (profile_id = auth.uid() OR public.is_admin());
CREATE POLICY "students_update_admin_or_self" ON public.students
    FOR UPDATE TO authenticated USING (profile_id = auth.uid() OR public.is_admin());

-- Parents & Links
CREATE POLICY "parents_select_scoped" ON public.parents
    FOR SELECT TO authenticated USING (profile_id = auth.uid() OR public.is_admin() OR public.is_teacher());
CREATE POLICY "parents_insert_self_or_admin" ON public.parents
    FOR INSERT TO authenticated WITH CHECK (profile_id = auth.uid() OR public.is_admin());

CREATE POLICY "parent_links_select" ON public.parent_student_links
    FOR SELECT TO authenticated
    USING (
        public.is_admin()
        OR EXISTS (SELECT 1 FROM public.parents p WHERE p.id = parent_id AND p.profile_id = auth.uid())
    );
CREATE POLICY "parent_links_insert" ON public.parent_student_links
    FOR INSERT TO authenticated
    WITH CHECK (
        public.is_admin()
        OR EXISTS (SELECT 1 FROM public.parents p WHERE p.id = parent_id AND p.profile_id = auth.uid())
    );

-- Enrollments
CREATE POLICY "enrollments_select" ON public.enrollments
    FOR SELECT TO authenticated
    USING (
        public.is_admin()
        OR public.is_teacher()
        OR EXISTS (SELECT 1 FROM public.students s WHERE s.id = student_id AND s.profile_id = auth.uid())
        OR public.is_linked_parent(student_id)
    );
CREATE POLICY "enrollments_admin_write" ON public.enrollments FOR ALL TO authenticated USING (public.is_admin()) WITH CHECK (public.is_admin());

-- Examinations, Questions & Answer Options
CREATE POLICY "exams_select" ON public.examinations
    FOR SELECT TO authenticated
    USING (
        public.is_admin()
        OR created_by = auth.uid()
        OR EXISTS (
            SELECT 1 FROM public.subjects sub
            JOIN public.teachers t ON t.id = sub.teacher_id
            WHERE sub.id = subject_id AND t.profile_id = auth.uid()
        )
        OR (
            is_published = TRUE AND EXISTS (
                SELECT 1 FROM public.students s WHERE s.class_id = examinations.class_id AND s.profile_id = auth.uid()
            )
        )
    );
CREATE POLICY "exams_teacher_admin_write" ON public.examinations
    FOR ALL TO authenticated
    USING (public.is_admin() OR public.is_teacher())
    WITH CHECK (public.is_admin() OR public.is_teacher());

CREATE POLICY "questions_select" ON public.questions
    FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "questions_teacher_admin_write" ON public.questions
    FOR ALL TO authenticated USING (public.is_admin() OR public.is_teacher()) WITH CHECK (public.is_admin() OR public.is_teacher());

CREATE POLICY "options_select" ON public.answer_options
    FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "options_teacher_admin_write" ON public.answer_options
    FOR ALL TO authenticated USING (public.is_admin() OR public.is_teacher()) WITH CHECK (public.is_admin() OR public.is_teacher());

-- Exam Attempts & Student Answers
CREATE POLICY "attempts_select" ON public.exam_attempts
    FOR SELECT TO authenticated
    USING (
        public.is_admin()
        OR public.is_teacher()
        OR EXISTS (SELECT 1 FROM public.students s WHERE s.id = student_id AND s.profile_id = auth.uid())
        OR public.is_linked_parent(student_id)
    );
CREATE POLICY "attempts_insert_student" ON public.exam_attempts
    FOR INSERT TO authenticated
    WITH CHECK (
        EXISTS (SELECT 1 FROM public.students s WHERE s.id = student_id AND s.profile_id = auth.uid())
    );
CREATE POLICY "attempts_update_scoped" ON public.exam_attempts
    FOR UPDATE TO authenticated
    USING (
        public.is_admin()
        OR public.is_teacher()
        OR EXISTS (SELECT 1 FROM public.students s WHERE s.id = student_id AND s.profile_id = auth.uid())
    );

CREATE POLICY "student_answers_scoped" ON public.student_answers
    FOR ALL TO authenticated
    USING (
        public.is_admin()
        OR public.is_teacher()
        OR EXISTS (
            SELECT 1 FROM public.exam_attempts ea
            JOIN public.students s ON s.id = ea.student_id
            WHERE ea.id = attempt_id AND s.profile_id = auth.uid()
        )
    )
    WITH CHECK (
        public.is_admin()
        OR public.is_teacher()
        OR EXISTS (
            SELECT 1 FROM public.exam_attempts ea
            JOIN public.students s ON s.id = ea.student_id
            WHERE ea.id = attempt_id AND s.profile_id = auth.uid()
        )
    );

-- Results
CREATE POLICY "results_select" ON public.results
    FOR SELECT TO authenticated
    USING (
        public.is_admin()
        OR public.is_teacher()
        OR (is_approved = TRUE AND (
            EXISTS (SELECT 1 FROM public.students s WHERE s.id = student_id AND s.profile_id = auth.uid())
            OR public.is_linked_parent(student_id)
        ))
    );
CREATE POLICY "results_teacher_admin_write" ON public.results
    FOR ALL TO authenticated USING (public.is_admin() OR public.is_teacher()) WITH CHECK (public.is_admin() OR public.is_teacher());

-- Attendance
CREATE POLICY "attendance_select" ON public.attendance
    FOR SELECT TO authenticated
    USING (
        public.is_admin()
        OR public.is_teacher()
        OR EXISTS (SELECT 1 FROM public.students s WHERE s.id = student_id AND s.profile_id = auth.uid())
        OR public.is_linked_parent(student_id)
    );
CREATE POLICY "attendance_teacher_admin_write" ON public.attendance
    FOR ALL TO authenticated USING (public.is_admin() OR public.is_teacher()) WITH CHECK (public.is_admin() OR public.is_teacher());

-- Assignments, Submissions, Learning Materials
CREATE POLICY "assignments_select" ON public.assignments FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "assignments_teacher_admin_write" ON public.assignments
    FOR ALL TO authenticated USING (public.is_admin() OR public.is_teacher()) WITH CHECK (public.is_admin() OR public.is_teacher());

CREATE POLICY "submissions_select" ON public.assignment_submissions
    FOR SELECT TO authenticated
    USING (
        public.is_admin()
        OR public.is_teacher()
        OR EXISTS (SELECT 1 FROM public.students s WHERE s.id = student_id AND s.profile_id = auth.uid())
    );
CREATE POLICY "submissions_student_insert" ON public.assignment_submissions
    FOR INSERT TO authenticated
    WITH CHECK (EXISTS (SELECT 1 FROM public.students s WHERE s.id = student_id AND s.profile_id = auth.uid()));
CREATE POLICY "submissions_teacher_update" ON public.assignment_submissions
    FOR UPDATE TO authenticated USING (public.is_admin() OR public.is_teacher());

CREATE POLICY "materials_select" ON public.learning_materials FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "materials_teacher_admin_write" ON public.learning_materials
    FOR ALL TO authenticated USING (public.is_admin() OR public.is_teacher()) WITH CHECK (public.is_admin() OR public.is_teacher());

-- Announcements & Parent Messages
CREATE POLICY "announcements_select" ON public.announcements FOR SELECT TO authenticated USING (TRUE);
CREATE POLICY "announcements_admin_teacher_write" ON public.announcements
    FOR ALL TO authenticated USING (public.is_admin() OR public.is_teacher()) WITH CHECK (public.is_admin() OR public.is_teacher());

CREATE POLICY "parent_messages_scoped" ON public.parent_messages
    FOR ALL TO authenticated
    USING (parent_profile_id = auth.uid() OR teacher_profile_id = auth.uid() OR public.is_admin())
    WITH CHECK (parent_profile_id = auth.uid() OR teacher_profile_id = auth.uid() OR public.is_admin());

-- Invoices & Payments (Invoices NEVER client-marked paid; payments updated by Edge Function service_role)
CREATE POLICY "invoices_select" ON public.invoices
    FOR SELECT TO authenticated
    USING (
        public.is_admin()
        OR EXISTS (SELECT 1 FROM public.students s WHERE s.id = student_id AND s.profile_id = auth.uid())
        OR public.is_linked_parent(student_id)
    );
CREATE POLICY "invoices_admin_write" ON public.invoices
    FOR ALL TO authenticated USING (public.is_admin()) WITH CHECK (public.is_admin());

CREATE POLICY "payments_select" ON public.payments
    FOR SELECT TO authenticated
    USING (
        public.is_admin()
        OR paid_by_profile_id = auth.uid()
        OR EXISTS (SELECT 1 FROM public.students s WHERE s.id = student_id AND s.profile_id = auth.uid())
        OR public.is_linked_parent(student_id)
    );

-- Notifications & Audit Logs
CREATE POLICY "notifications_own" ON public.notifications
    FOR ALL TO authenticated
    USING (user_id = auth.uid() OR public.is_admin())
    WITH CHECK (user_id = auth.uid() OR public.is_admin());

CREATE POLICY "audit_logs_insert_auth" ON public.audit_logs
    FOR INSERT TO authenticated WITH CHECK (actor_id = auth.uid() OR public.is_admin());
CREATE POLICY "audit_logs_admin_select" ON public.audit_logs
    FOR SELECT TO authenticated USING (public.is_admin());

-- 18. Seed Initial School Settings & Default Academic Session
INSERT INTO public.school_settings (school_name, motto)
SELECT 'Green Light Academy', 'Excellence Through Knowledge & Integrity'
WHERE NOT EXISTS (SELECT 1 FROM public.school_settings);

INSERT INTO public.academic_sessions (name, term, start_date, end_date, is_active)
SELECT '2026/2027', 'First Term', '2026-09-07', '2026-12-18', TRUE
WHERE NOT EXISTS (SELECT 1 FROM public.academic_sessions);

-- 19. Storage Buckets (Run via Supabase SQL Editor)
INSERT INTO storage.buckets (id, name, public)
VALUES
  ('passports', 'passports', TRUE),
  ('question-images', 'question-images', TRUE),
  ('materials', 'materials', TRUE),
  ('submissions', 'submissions', FALSE),
  ('school-assets', 'school-assets', TRUE)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY "Public read on public buckets" ON storage.objects
    FOR SELECT USING (bucket_id IN ('passports', 'question-images', 'materials', 'school-assets'));

CREATE POLICY "Authenticated upload to school buckets" ON storage.objects
    FOR INSERT TO authenticated WITH CHECK (bucket_id IN ('passports', 'question-images', 'materials', 'submissions', 'school-assets'));
