# Design QA

**Comparison Target**
- Source visual truth: `/Users/hanyongliang/.codex/generated_images/019f36ae-93e0-7b43-9a43-73a677285a66/exec-716d77fe-342b-4cd8-ab9f-79576f9e385b.png`
- Implementation screenshot: `/Users/hanyongliang/Downloads/codex-project/projects/临期提醒app/docs/design-qa-implementation.png`
- Full-view comparison: `/tmp/daoqi-design-qa-comparison.png`
- Viewport and state: 390 x 844 CSS px, light theme, valid sentence parsed and ready to confirm.
- Source pixels: 853 x 1844, normalized to 390 x 844 for comparison.
- Implementation pixels: 390 x 844 at device scale factor 1.

**Findings**
- No actionable P0, P1, or P2 visual differences.
- The implementation intentionally replaces "要提醒什么？" with the approved "添加到期提醒" title and adds the approved one-sentence instruction.
- The implementation intentionally uses a solid primary color instead of the generated mock's slight button gradient, matching the product's restrained visual rules.
- The recent rows retain reminder lead time as useful product data while preserving the mock's unframed list structure.

**Required Fidelity Surfaces**
- Fonts and typography: system sans-serif stack, hierarchy, weights, wrapping, and zero letter spacing are consistent and readable.
- Spacing and layout rhythm: 20 px horizontal margins, 8 px radii, input, parsed confirmation, primary action, and recent list align without clipping or overflow.
- Colors and visual tokens: white surface, graphite text, blue action, green parsed state, and urgency colors match the selected direction.
- Image quality and asset fidelity: the design has no raster assets; UI icons use Lucide in the preview and Material icons in Compose.
- Copy and content: approved title, input guidance, example, parsed fields, action label, and recent-record metadata are present.

**Interaction Evidence**
- Valid Chinese and ISO-style input updates parsed title, date, and reminder days.
- Clearing input disables confirmation.
- Invalid input hides the parsed panel and shows an error dialog.
- Valid confirmation shows success feedback.
- Browser console errors: none.

**Focused Region Comparison**
- A separate crop was not needed because the normalized 780 x 844 side-by-side image keeps the input, parsed rows, primary action, and recent records readable at full view.

**Comparison History**
- Initial capture showed the browser's cached previous UI; the preview was reopened with a cache-busting URL and recaptured.
- Post-fix evidence is the implementation screenshot listed above. No visual source changes were required after the normalized comparison.

**Residual Validation Gap**
- The Android APK compiled successfully, but no physical Android device screenshot was available in this run. Final device font rasterization and system insets remain for later real-device verification.

final result: passed

## 2026-09-04 设置更新验收

- 保留原来的布局和蓝色强调色；只保留中文，不增加语言选择或英文解析。
- 设置改为独立页面：主题、系统通知入口、测试通知、每日提醒时间、默认提前天数、版本和隐私说明。
- 浏览器验证：浅色、深色、跟随系统、手动主题优先、刷新后设置保留、保存和取消、非法天数拒绝、默认值应用、明确提前时间优先、已有展示记录不变。
- 视口：390×844、320×568、1440×1000。小屏设置可滚动，没有横向溢出；深色主页和设置已截图检查。
- 截图：`output/playwright/settings-light.png`、`output/playwright/settings-dark.png`。
- 浏览器通知按钮明确说明不能代替安卓实机测试，未模拟“通知发送成功”。控制台未发现错误。
- 安卓：9 项单元测试通过；Lint 无错误、12 项警告；签名 Release APK 构建成功，APK v2 签名验证通过。
- 尚未验证：安卓真机渲染、持久化、系统权限往返、后台定时通知和测试通知送达。当前 ADB 设备列表为空。
- 反馈入口等待提供联系邮箱，备份导入导出不属于本次范围。
