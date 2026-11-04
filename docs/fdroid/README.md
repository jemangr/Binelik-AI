# Binelik AI στο F-Droid

Το F-Droid χτίζει την εφαρμογή μόνο του από τον κώδικα και την υπογράφει με δικό του κλειδί. Γι' αυτό:

- Το build του F-Droid γίνεται με `-PnoUpdateCheck`: χωρίς τον έλεγχο νέας έκδοσης από το GitHub, γιατί τις ενημερώσεις τις φέρνει το F-Droid.
- Τα κείμενα και τα screenshots για τη σελίδα του είναι στο `fastlane/metadata/android/` (ελληνικά και αγγλικά).
- Έχει την ένδειξη **NonFreeNet**, γιατί μιλά με το Gemini ή το DeepSeek.

## Τι κάνει ο δημιουργός (μία φορά)

1. Λογαριασμός στο https://gitlab.com.
2. Fork του https://gitlab.com/fdroid/fdroiddata.
3. Νέο αρχείο `metadata/app.binelik.yml` με το περιεχόμενο του `docs/fdroid/app.binelik.yml`.
4. Merge request με τίτλο «New app: Binelik AI». Οι εθελοντές του F-Droid το ελέγχουν (συνήθως μέρες ή εβδομάδες) και μπορεί να ζητήσουν αλλαγές.

Το F-Droid βρίσκει τις νέες εκδόσεις από τα tags `v*` του repo, που φτιάχνει το release workflow.
