// 光棱坦克（Prism Tank）核心算法
//
// 参考实现：https://github.com/Uyanide/Mirage_Decode （web/src/services/process/fallback）
// 原理：里图被压进一个窄的色阶区间，再用交错的花纹（条纹）把表图按宽色阶铺到其余像素上。
//       肉眼（或缩略图）看到的是表图，按阈值把窄区间像素拉回全色阶后显形的是里图。
//
// 本文件不依赖 JNI / Android，只做纯像素运算，方便在宿主机上单独编译校验。
//
// 像素格式统一为 Android Bitmap 的 ARGB_8888 内存布局（小端 uint32）：
//   px = (a << 24) | (b << 16) | (g << 8) | r

#pragma once

#include <cstdint>
#include <cmath>
#include <cstring>
#include <cstdlib>

namespace prism {

inline int clamp_int(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

inline int px_r(uint32_t px) { return (int)(px & 0xFFu); }
inline int px_g(uint32_t px) { return (int)((px >> 8) & 0xFFu); }
inline int px_b(uint32_t px) { return (int)((px >> 16) & 0xFFu); }
inline int px_a(uint32_t px) { return (int)((px >> 24) & 0xFFu); }

inline uint32_t make_px(int r, int g, int b, int a) {
    return ((uint32_t)(a & 0xFF) << 24) | ((uint32_t)(b & 0xFF) << 16) |
           ((uint32_t)(g & 0xFF) << 8) | (uint32_t)(r & 0xFF);
}

// 亮度（与参考实现一致：r * 0.299 + g * 0.587 + b * 0.114）
inline double luma(int r, int g, int b) {
    return (double)r * 0.299 + (double)g * 0.587 + (double)b * 0.114;
}

// ---------------------------------------------------------------------------
// 通用：对比度 LUT（-255 ~ 255，与参考实现的 adjustContrast 完全一致）
// ---------------------------------------------------------------------------
inline void build_contrast_lut(int contrast, uint8_t lut[256]) {
    int c = clamp_int(contrast, -255, 255);
    const double factor = (259.0 * (double)(c + 255)) / (255.0 * (double)(259 - c));
    for (int i = 0; i < 256; ++i) {
        double v = factor * ((double)i - 128.0) + 128.0;
        // Uint8ClampedArray 的语义：四舍五入（Rust/JS 的 round-half-to-even），再钳位
        int iv = (int)lrint(v);
        lut[i] = (uint8_t)clamp_int(iv, 0, 255);
    }
}

inline void build_identity_lut(uint8_t lut[256]) {
    for (int i = 0; i < 256; ++i) lut[i] = (uint8_t)i;
}

// ---------------------------------------------------------------------------
// 编码
// ---------------------------------------------------------------------------

// 花纹判定：返回 true 表示该像素取表图（否则取里图）
inline bool encode_is_cover(int x, int y, int slope, int gap, bool is_row) {
    if (gap < 1) gap = 1;
    if (slope == 0) {
        int v = is_row ? y : x;
        return (v % (gap + 1)) < gap;
    }
    double v = is_row ? ((double)y / (double)slope + (double)x) : ((double)x / (double)slope + (double)y);
    double m = fmod(v, (double)(gap + 1));
    return m < (double)gap;
}

inline int scale_inner(int v, int t, bool is_reverse) {
    if (is_reverse) return 255 - t + (v * t) / 255;
    return (v * t) / 255;
}

inline int scale_cover(int v, int t, bool is_reverse) {
    if (is_reverse) return (v * (255 - t)) / 255;
    return t + (v * (255 - t)) / 255;
}

struct EncodeConfig {
    int inner_threshold = 24;
    int cover_threshold = 42;
    int slope = 1;
    int gap = 1;
    bool is_row = true;
    bool is_reverse = false;
};

// inner_lut / cover_lut：先取灰度、再做对比度之后要套用的通道 LUT
// （不需要调整时传入单位 LUT 即可）
inline void encode(const uint32_t* inner, const uint32_t* cover, uint32_t* out, int width, int height,
                   const EncodeConfig& cfg, const uint8_t inner_lut[256], const uint8_t cover_lut[256],
                   bool inner_gray, bool cover_gray) {
    const int inner_t = clamp_int(cfg.inner_threshold, 0, 255);
    const int cover_t = clamp_int(cfg.cover_threshold, 0, 255);

    for (int y = 0, i = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x, ++i) {
            const bool is_cover = encode_is_cover(x, y, cfg.slope, cfg.gap, cfg.is_row);
            const uint32_t src = is_cover ? cover[i] : inner[i];
            int r = px_r(src), g = px_g(src), b = px_b(src);
            const int a = px_a(src);

            if (is_cover ? cover_gray : inner_gray) {
                const int y8 = clamp_int((int)lrint(luma(r, g, b)), 0, 255);
                r = g = b = y8;
            }
            const uint8_t* lut = is_cover ? cover_lut : inner_lut;
            r = lut[r];
            g = lut[g];
            b = lut[b];

            if (is_cover) {
                out[i] = make_px(scale_cover(r, cover_t, cfg.is_reverse),
                                 scale_cover(g, cover_t, cfg.is_reverse),
                                 scale_cover(b, cover_t, cfg.is_reverse), a);
            } else {
                out[i] = make_px(scale_inner(r, inner_t, cfg.is_reverse),
                                 scale_inner(g, inner_t, cfg.is_reverse),
                                 scale_inner(b, inner_t, cfg.is_reverse), a);
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 解码
// ---------------------------------------------------------------------------

enum DecodeMethod {
    DECODE_BLACK = 0,        // 区间外像素填黑
    DECODE_WHITE = 1,        // 填白
    DECODE_TRANSPARENT = 2,  // 填透明
    DECODE_COPY_LEFT = 3,    // 复制左侧像素
    DECODE_COPY_TOP = 4,     // 复制上方像素
    DECODE_LTAVG = 5,        // 左上平均值起步 + 迭代平滑填充
};

struct DecodeConfig {
    int lower_threshold = 0;
    int higher_threshold = 24;
    int method = DECODE_LTAVG;
    int iterations = 16;
};

namespace detail {

struct Offset {
    int dx;
    int dy;
    double w;
};

// 与参考实现 webgl/decode.ts 的 fillFS 一致：24 邻域，sigma = 1.0 的高斯权重
inline const Offset* fill_offsets() {
    static const Offset offsets[24] = {
        {-1, 0, 0.6065}, {1, 0, 0.6065},
        {0, -1, 0.6065}, {0, 1, 0.6065},
        {-1, -1, 0.3679}, {1, 1, 0.3679},
        {-1, 1, 0.3679}, {1, -1, 0.3679},
        {-2, 0, 0.1353}, {2, 0, 0.1353},
        {0, -2, 0.1353}, {0, 2, 0.1353},
        {-2, -2, 0.0183}, {2, 2, 0.0183},
        {-2, 2, 0.0183}, {2, -2, 0.0183},
        {-1, -2, 0.0821}, {1, 2, 0.0821},
        {-2, -1, 0.0821}, {2, 1, 0.0821},
        {-1, 2, 0.0821}, {1, -2, 0.0821},
        {-2, 1, 0.0821}, {2, -1, 0.0821},
    };
    return offsets;
}

inline void copy_left(uint32_t* out, int width, int i) {
    if ((i % width) == 0) {
        out[i] = make_px(0, 0, 0, 255);
    } else {
        out[i] = out[i - 1];
    }
}

inline void copy_top(uint32_t* out, int width, int i) {
    if (i - width < 0) {
        out[i] = make_px(0, 0, 0, 255);
    } else {
        out[i] = out[i - width];
    }
}

}  // namespace detail

// 迭代平滑：仅对落在区间外（需要填充）的像素做加权平均，被填好的像素在下一轮作为已知像素参与
inline void fill_iterations(uint32_t* color, uint8_t* mask, uint32_t* tmp_color, uint8_t* tmp_mask,
                            int width, int height, int iterations) {
    if (iterations <= 0) return;
    const detail::Offset* offsets = detail::fill_offsets();

    for (int iter = 0; iter < iterations; ++iter) {
        bool any_filled = false;
        for (int y = 0, i = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x, ++i) {
                if (!mask[i]) {
                    tmp_color[i] = color[i];
                    tmp_mask[i] = 0;
                    continue;
                }
                double sum_r = 0, sum_g = 0, sum_b = 0, sum_a = 0, wsum = 0;
                int count = 0;
                for (int k = 0; k < 24; ++k) {
                    const int nx = x + offsets[k].dx;
                    const int ny = y + offsets[k].dy;
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                    const int ni = ny * width + nx;
                    if (mask[ni]) continue;
                    const uint32_t p = color[ni];
                    sum_r += (double)px_r(p) * offsets[k].w;
                    sum_g += (double)px_g(p) * offsets[k].w;
                    sum_b += (double)px_b(p) * offsets[k].w;
                    sum_a += (double)px_a(p) * offsets[k].w;
                    wsum += offsets[k].w;
                    ++count;
                }
                if (count > 0 && wsum > 0.0) {
                    tmp_color[i] = make_px((int)lrint(sum_r / wsum), (int)lrint(sum_g / wsum),
                                           (int)lrint(sum_b / wsum), (int)lrint(sum_a / wsum));
                    tmp_mask[i] = 0;
                    any_filled = true;
                } else {
                    tmp_color[i] = color[i];
                    tmp_mask[i] = 1;
                }
            }
        }
        std::memcpy(color, tmp_color, (size_t)width * (size_t)height * sizeof(uint32_t));
        std::memcpy(mask, tmp_mask, (size_t)width * (size_t)height);
        if (!any_filled) break;
    }
}

// 解码：out 需要与 src 同尺寸；mask/tmp_color/tmp_mask 为可选工作区（method == LTAVG 时必须提供）
// lut：解码完成后再套用的对比度 LUT（不需要时传入单位 LUT）
inline void decode(const uint32_t* src, uint32_t* out, int width, int height, const DecodeConfig& cfg,
                   const uint8_t lut[256], uint8_t* mask, uint32_t* tmp_color, uint8_t* tmp_mask) {
    const int total = width * height;
    if (total <= 0) return;

    int lower = clamp_int(cfg.lower_threshold, 0, 255);
    int higher = clamp_int(cfg.higher_threshold, 0, 255);
    const int method = cfg.method;

    if (higher <= lower) {
        for (int i = 0; i < total; ++i) {
            out[i] = make_px(0, 0, 0, px_a(src[i]));
            if (mask) mask[i] = 0;
        }
    } else {
        const double ratio = 255.0 / (double)(higher - lower);
        for (int y = 0, i = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x, ++i) {
                const uint32_t p = src[i];
                const int r = px_r(p), g = px_g(p), b = px_b(p), a = px_a(p);
                const double l = luma(r, g, b);
                if (l >= (double)lower && l <= (double)higher) {
                    out[i] = make_px(clamp_int((int)lrint((r - lower) * ratio), 0, 255),
                                     clamp_int((int)lrint((g - lower) * ratio), 0, 255),
                                     clamp_int((int)lrint((b - lower) * ratio), 0, 255), a);
                    if (mask) mask[i] = 0;
                } else {
                    if (mask) mask[i] = 1;
                    switch (method) {
                        case DECODE_WHITE:
                            out[i] = make_px(255, 255, 255, 255);
                            break;
                        case DECODE_TRANSPARENT:
                            out[i] = make_px(0, 0, 0, 0);
                            break;
                        case DECODE_COPY_LEFT:
                            detail::copy_left(out, width, i);
                            break;
                        case DECODE_COPY_TOP:
                            detail::copy_top(out, width, i);
                            break;
                        case DECODE_LTAVG: {
                            if (i == 0) {
                                out[i] = make_px(0, 0, 0, 255);
                            } else if (y == 0) {
                                detail::copy_left(out, width, i);
                            } else if (x == 0) {
                                detail::copy_top(out, width, i);
                            } else {
                                const uint32_t left = out[i - 1];
                                const uint32_t top = out[i - width];
                                const uint32_t lt = out[i - width - 1];
                                out[i] = make_px((int)lrint((px_r(left) + px_r(top) + px_r(lt)) / 3.0),
                                                 (int)lrint((px_g(left) + px_g(top) + px_g(lt)) / 3.0),
                                                 (int)lrint((px_b(left) + px_b(top) + px_b(lt)) / 3.0),
                                                 (int)lrint((px_a(left) + px_a(top) + px_a(lt)) / 3.0));
                            }
                            break;
                        }
                        case DECODE_BLACK:
                        default:
                            out[i] = make_px(0, 0, 0, 255);
                            break;
                    }
                }
            }
        }
    }

    if (method == DECODE_LTAVG && mask != nullptr && tmp_color != nullptr && tmp_mask != nullptr &&
        cfg.iterations > 0) {
        // 大图上限制总迭代量，避免滑动参数时卡死（每轮约 24 次邻域采样）
        int iterations = cfg.iterations;
        const long double budget = 2.0e8L;
        if ((long double)total * (long double)iterations > budget) {
            iterations = (int)(budget / (long double)total);
            if (iterations < 1) iterations = 1;
        }
        fill_iterations(out, mask, tmp_color, tmp_mask, width, height, iterations);
    }

    // 最后统一套对比度
    for (int i = 0; i < total; ++i) {
        const uint32_t p = out[i];
        out[i] = make_px(lut[px_r(p)], lut[px_g(p)], lut[px_b(p)], px_a(p));
    }
}

}  // namespace prism
