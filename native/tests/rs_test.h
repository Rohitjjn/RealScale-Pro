// RealScale-Pro — minimal native test framework (no external deps).
//
// Usage:
//   RS_TEST(fn_name) { RS_ASSERT(cond); return true; }
//   int main() { return rs::run_all_tests() ? 0 : 1; }
//
// Copyright 2026 RealScale-Pro contributors. MIT License.
#ifndef RS_TEST_H
#define RS_TEST_H

#include <cstdio>
#include <functional>
#include <string>
#include <vector>

namespace rs {

struct TestCase {
    std::string name;
    std::function<bool()> fn;
};

inline std::vector<TestCase>& tests() {
    static std::vector<TestCase> t;
    return t;
}

inline bool run_all_tests() {
    int failed = 0;
    for (const TestCase& tc : tests()) {
        bool ok = false;
        try {
            ok = tc.fn();
        } catch (const std::exception& e) {
            std::fprintf(stderr, "  exception: %s\n", e.what());
        } catch (...) {
            std::fprintf(stderr, "  unknown exception\n");
        }
        std::printf("[%s] %s\n", ok ? "PASS" : "FAIL", tc.name.c_str());
        if (!ok) {
            ++failed;
        }
    }
    std::printf("\n%d/%zu passed\n",
                static_cast<int>(tests().size()) - failed, tests().size());
    return failed == 0;
}

} // namespace rs

#define RS_TEST(name)                                                        \
    static bool rs_test_##name();                                            \
    namespace {                                                              \
    struct rs_reg_##name {                                                   \
        rs_reg_##name() {                                                    \
            ::rs::tests().push_back({#name, rs_test_##name});                \
        }                                                                    \
    } rs_reg_instance_##name;                                                \
    }                                                                        \
    static bool rs_test_##name()

#define RS_ASSERT(cond)                                                      \
    do {                                                                     \
        if (!(cond)) {                                                       \
            std::fprintf(stderr, "  assert failed: %s (%s:%d)\n", #cond,      \
                         __FILE__, __LINE__);                                \
            return false;                                                    \
        }                                                                    \
    } while (0)

#define RS_ASSERT_EQ(a, b)                                                   \
    do {                                                                     \
        auto va_ = (a);                                                      \
        auto vb_ = (b);                                                      \
        if (!(va_ == vb_)) {                                                 \
            std::fprintf(stderr, "  assert failed: %s == %s (%lld != %lld, %s:%d)\n", \
                         #a, #b, (long long)va_, (long long)vb_, __FILE__, __LINE__); \
            return false;                                                    \
        }                                                                    \
    } while (0)

#endif // RS_TEST_H
