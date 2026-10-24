# Semippu (சேமிப்பு) — savings scheme and loan calculators for Android

Semippu means "savings" in Tamil. It works out what India's popular savings schemes pay, at the
government's current rates, with a year-by-year table. A Loans tab works out the EMI on a home, car
or personal loan, and how many months and how much interest a part payment saves. The app has no `INTERNET` permission:
nothing you type leaves the phone.

**Download:** the latest build is the `Semippu.apk` in the
[`semippu-latest` release](https://github.com/Akrishna87/Akrishna87/releases/tag/semippu-latest), or from the
[download page](https://akrishna87.github.io/Akrishna87/apps/).

## Calculators

| Calculator | Rate it starts with | How it's worked out |
|---|---|---|
| PPF | 7.1% | A deposit by 5 April each year, compounded yearly, for 15 years (or 20, 25… with extensions) |
| Sukanya Samriddhi | 8.2% | Deposits for 15 years; matures at 21 years, earning interest in between |
| NSC | 7.7% | Compounded yearly for 5 years, paid at maturity |
| KVP | 7.5% | Doubles your money; 115 months at the current rate |
| SCSS | 8.2% | Interest paid every quarter for 5 years, then the deposit is returned |
| Monthly Income Scheme | 7.4% | Interest paid every month for 5 years, then the deposit is returned |
| Fixed Deposit | 7% (an example; enter your bank's) | Compounded quarterly; months past the last full quarter earn simple interest |
| Recurring Deposit | 6.7% (Post Office 5-year RD) | Monthly deposits, compounded quarterly |
| SIP | 12% (an assumption, not guaranteed) | Monthly investments at the start of each month, compounded monthly |

Every rate can be changed, to try a different rate or a bank's own.

The government rates are those for **1 October – 31 December 2026**. The Ministry of Finance sets
them every quarter; when they change, update `Rates` at the top of
[`Schemes.kt`](app/src/main/java/io/github/akrishna87/semippu/Schemes.kt).

## Loans

Home, car and personal loans, each starting from an example amount, rate and tenure (banks set their
own rates, so enter yours):

- **EMI**, total interest and total cost.
- **Processing fee:** the total cost with the fee, and the real rate once the fee is counted.
- **A loan that's already running:** enter the EMIs already paid to see what's still owed and the
  interest still to pay. Part payments then start from today's balance.
- **Part payments**, once, every year or every month, from any EMI on. Choose **Shorter loan** (same
  EMI, see how many months sooner it ends and the new end month) or **Lower EMI** (same end date,
  see the new EMI). Both show the interest saved.
- A year-by-year table of principal, interest and balance.

Each month the interest is worked out on the balance, the rest of the EMI comes off the balance, and
then any part payment.

## Checked against published figures

The unit tests check the sums against figures that banks, the Post Office and fund houses publish:
₹1.5 lakh a year in PPF for 15 years at 7.1% is ₹40,68,209; ₹1,000 a month in a 5-year RD at 6.7% is
₹71,366; ₹10,000 a month in a SIP for 10 years at 12% is ₹23,23,391. KVP doubles in 115 months at 7.5%.
A ₹10 lakh loan at 10% for 20 years has an EMI of ₹9,650 and ₹13,16,052 of interest. Part payments are
checked against the closed-form number of months left.

## Not included

- Tax: what you'd owe on taxable interest, or 80C savings.
- EPF, NPS and Atal Pension Yojana.
- Prepayment charges on loans, and floating rates that change during the loan.
- Comparing schemes side by side.

## How it's built

Jetpack Compose and Material 3, with no other libraries.

| File | What's there |
|---|---|
| `app/src/main/java/…/Schemes.kt` | The rates, each scheme's inputs and rules, and the calculations. Plain Kotlin, no Android |
| `app/src/main/java/…/Loans.kt` | EMI, the month-by-month repayment with part payments, and the real rate with a fee. Plain Kotlin |
| `app/src/main/java/…/ui/Screens.kt` | The Savings and Loans tabs and the savings calculator screen |
| `app/src/main/java/…/ui/LoanScreen.kt` | The loan calculator screen |
| `app/src/test/…/SchemesTest.kt`, `LoansTest.kt` | The tests against published figures |
| `ci/smoke-test.sh` | The emulator test: PPF and SCSS, then a home loan's EMI and a part payment |

## Building

```
cd semippu-android
./gradlew testReleaseUnitTest assembleRelease
```

The release build is signed with `signing/sideload.keystore` so that every build installs as an update
over the last one. The [workflow](../.github/workflows/semippu-apk.yml) runs the unit tests, builds the APK,
runs the smoke test on an Android 14 emulator and publishes the APK with the test's screenshots.
